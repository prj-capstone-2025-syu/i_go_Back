package com.example.demo.service;

import com.example.demo.dto.routine.CalculatedRoutineItemTime;
import com.example.demo.dto.weather.WeatherResponse;
import com.example.demo.entity.fcm.Notification;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.user.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleNotificationService {

    private final ScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;
    private final FCMService fcmService;
    private final RoutineService routineService;
    private final WeatherApiService weatherApiService;

    @Value("${igo.notification.supplies.minutes.before:5}")
    private int suppliesNotificationMinutesBefore;

    @Value("${igo.notification.schedule.title.start}")
    private String scheduleStartTitle;

    @Value("${igo.notification.supplies.title}")
    private String suppliesTitle;

    @Value("${igo.notification.location.arrival}")
    private String arrivalLocationPrefix;

    @Value("${igo.notification.location.departure}")
    private String departureLocationPrefix;

    private static final String NOTIFICATION_TYPE_SCHEDULE_START = "SCHEDULE_START";
    private static final String NOTIFICATION_TYPE_ROUTINE_ITEM_START = "ROUTINE_ITEM_START";
    private static final String NOTIFICATION_TYPE_SUPPLIES_REMINDER = "SUPPLIES_REMINDER";
    private static final String NOTIFICATION_TYPE_ROUTINE_START_REMINDER = "ROUTINE_START_REMINDER";

    @Scheduled(cron = "0 * * * * ?") // 매 분 0초에 실행
    public void sendScheduleAndRoutineNotifications() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        log.info("스케줄 및 루틴 알림 작업 실행: 현재 시간 {}", now);

        // 1. 루틴 시작 1시간 전 알림 처리
        processRoutineStartReminders(now);

        // 2. 스케줄 시작 알림 및 준비물 알림 처리
        processPendingScheduleNotifications(now);

        // 3. 루틴 아이템 시작 알림 처리
        processInProgressScheduleNotifications(now);
    }

    // 루틴 시작 1시간 전 알림 처리 (날씨 정보 포함)
    private void processRoutineStartReminders(LocalDateTime now) {
        LocalDateTime oneHourLater = now.plusHours(1);
        LocalDateTime searchEndTime = oneHourLater.plusMinutes(1);

        log.info("🔍 [ScheduleNotificationService] 1시간 전 알림 처리 시작 - 현재시간: {}, 검색범위: {} ~ {}",
                now, oneHourLater, searchEndTime);

        List<Schedule> upcomingRoutineSchedules = scheduleRepository.findByStartTimeAndStatusAndRoutineIdNotNull(
                oneHourLater, searchEndTime, Schedule.ScheduleStatus.PENDING);

        log.info("📋 [ScheduleNotificationService] 1시간 후 시작되는 루틴 스케줄 {}개 발견", upcomingRoutineSchedules.size());

        for (Schedule schedule : upcomingRoutineSchedules) {
            log.debug("🔎 [ScheduleNotificationService] 발견된 스케줄 - ID: {}, 제목: '{}', 시작시간: {}, 루틴ID: {}",
                    schedule.getId(), schedule.getTitle(), schedule.getStartTime(), schedule.getRoutineId());
        }

        for (Schedule schedule : upcomingRoutineSchedules) {
            User user = schedule.getUser();
            if (!isValidNotificationUser(user) || !user.isNotifyRoutineProgress()) {
                log.debug("⚠️ [ScheduleNotificationService] 사용자 알림 조건 미충족 - User ID: {}, FCM Token: {}, NotifyRoutineProgress: {}",
                        user.getId(),
                        (user.getFcmToken() != null ? "있음" : "없음"),
                        user.isNotifyRoutineProgress());
                continue;
            }

            Optional<Notification> existingNotification = notificationRepository
                    .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

            if (existingNotification.isEmpty()) {
                log.info("📤 [ScheduleNotificationService] 1시간 전 알림 전송 시작 - Schedule ID: {}, User ID: {}",
                        schedule.getId(), user.getId());
                sendRoutineStartReminderWithWeather(schedule, user);
            } else {
                log.debug("🔄 [ScheduleNotificationService] 이미 1시간 전 알림 전송됨 - Schedule ID: {}, Notification ID: {}",
                        schedule.getId(), existingNotification.get().getId());
            }
        }

        log.info("✅ [ScheduleNotificationService] 1시간 전 알림 처리 완료");
    }

    // PENDING 상태의 스케줄들에 대한 알림 처리
    private void processPendingScheduleNotifications(LocalDateTime now) {
        LocalDateTime scheduleNotificationRangeEnd = now.plusMinutes(5);

        List<Schedule> pendingSchedules = scheduleRepository.findByStartTimeBetweenAndStatusAndUserFcmTokenIsNotNull(
                now,
                scheduleNotificationRangeEnd.plusMinutes(suppliesNotificationMinutesBefore),
                Schedule.ScheduleStatus.PENDING
        );

        log.info("📋 [ScheduleNotificationService] PENDING 스케줄 처리 - 총 {}개", pendingSchedules.size());

        for (Schedule schedule : pendingSchedules) {
            User user = schedule.getUser();
            if (!isValidNotificationUser(user)) {
                continue;
            }

            // 루틴이 있는 스케줄인 경우, 루틴 시작 시간 체크
            if (schedule.getRoutineId() != null && user.isNotifyRoutineProgress()) {
                checkAndStartRoutine(schedule, user, now);
            }

            // 준비물 알림 처리
            processSuppliesNotification(schedule, user, now);

            // 스케줄 시작 알림 처리
            processScheduleStartNotification(schedule, user, now);
        }
    }

    // 루틴 시작 시간 체크 및 스케줄 IN_PROGRESS 전환
    private void checkAndStartRoutine(Schedule schedule, User user, LocalDateTime now) {
        try {
            List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
                    schedule.getRoutineId(), schedule.getStartTime());

            if (calculatedItems.isEmpty()) {
                return;
            }

            LocalDateTime routineStartTime = calculatedItems.get(0).getStartTime();

            log.info("🕐 [ScheduleNotificationService] 루틴 시작 시간 체크");
            log.info("   ├─ 스케줄 ID: {}, 제목: '{}'", schedule.getId(), schedule.getTitle());
            log.info("   ├─ 현재 시간: {}", now);
            log.info("   ├─ 루틴 시작 시간: {}", routineStartTime);
            log.info("   └─ 스케줄 시작 시간: {}", schedule.getStartTime());

            if (routineStartTime.isEqual(now) || routineStartTime.isBefore(now)) {
                log.info("   ✅ 루틴 시작 조건 충족! 스케줄을 IN_PROGRESS로 전환");
                markScheduleAsInProgress(schedule);

                for (CalculatedRoutineItemTime itemTime : calculatedItems) {
                    if (itemTime.getStartTime().isEqual(now)) {
                        log.info("   📤 루틴 아이템 알림 전송: '{}'", itemTime.getRoutineItemName());
                        processRoutineItemStartNotification(schedule, user, itemTime);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 루틴 시작 체크 실패 - Schedule ID: {}, 에러: {}",
                    schedule.getId(), e.getMessage(), e);
        }
    }

    // IN_PROGRESS 상태의 스케줄들에 대한 루틴 아이템 알림 처리
    private void processInProgressScheduleNotifications(LocalDateTime now) {
        List<Schedule> inProgressSchedules = scheduleRepository.findByStatusAndUserFcmTokenIsNotNull(Schedule.ScheduleStatus.IN_PROGRESS);

        for (Schedule schedule : inProgressSchedules) {
            User user = schedule.getUser();
            if (!isValidNotificationUser(user) || !user.isNotifyRoutineProgress() || schedule.getRoutineId() == null) {
                if (schedule.getRoutineId() == null && isScheduleCompleted(schedule, now)) {
                    markScheduleAsCompleted(schedule);
                }
                continue;
            }

            processRoutineItemNotifications(schedule, user, now);
        }
    }

    // 준비물 알림
    private void processSuppliesNotification(Schedule schedule, User user, LocalDateTime now) {
        LocalDateTime suppliesNotificationTime = schedule.getStartTime().minusMinutes(suppliesNotificationMinutesBefore);

        if (user.isNotifySupplies() &&
            schedule.getSupplies() != null &&
            !schedule.getSupplies().isEmpty() &&
            suppliesNotificationTime.isEqual(now)) {

            Optional<Notification> existingNotification = notificationRepository
                    .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);

            if (existingNotification.isEmpty()) {
                String title = suppliesTitle;
                String body = String.format("'%s'이(가) %d분 후 시작돼요!\n📋 준비물: %s",
                        schedule.getTitle(), suppliesNotificationMinutesBefore, schedule.getSupplies());

                Map<String, String> data = createNotificationData(schedule.getId().toString(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);
                sendAndSaveNotification(user, title, body, data, schedule.getId(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);
            }
        }
    }

    // 스케줄 시작 알림 처리
    private void processScheduleStartNotification(Schedule schedule, User user, LocalDateTime now) {
        if (user.isNotifyNextSchedule() && schedule.getStartTime().isEqual(now)) {
            Optional<Notification> existingNotification = notificationRepository
                    .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_SCHEDULE_START);

            if (existingNotification.isEmpty()) {
                String title = scheduleStartTitle;
                String body = createScheduleStartBody(schedule);

                Map<String, String> data = createNotificationData(schedule.getId().toString(), NOTIFICATION_TYPE_SCHEDULE_START);
                sendAndSaveNotification(user, title, body, data, schedule.getId(), NOTIFICATION_TYPE_SCHEDULE_START);
                processRoutineItemsAtScheduleStart(schedule, user, now);

                markScheduleAsInProgress(schedule);
            } else {
                if (schedule.getStatus() == Schedule.ScheduleStatus.PENDING) {
                    markScheduleAsInProgress(schedule);
                }
            }
        }
    }

    // 루틴 아이템 알림 처리
    private void processRoutineItemNotifications(Schedule schedule, User user, LocalDateTime now) {
        log.info("🔔 [ScheduleNotificationService] 루틴 아이템 알림 처리 시작");
        log.info("   ├─ 스케줄 ID: {}, 제목: '{}'", schedule.getId(), schedule.getTitle());
        log.info("   ├─ 현재 시간: {}", now);
        log.info("   └─ 루틴 ID: {}", schedule.getRoutineId());

        List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
                schedule.getRoutineId(), schedule.getStartTime());
        boolean allItemsCompleted = true;

        for (CalculatedRoutineItemTime itemTime : calculatedItems) {
            log.debug("   🔸 아이템 체크: '{}'", itemTime.getRoutineItemName());
            log.debug("      ├─ 시작 시간: {} (현재와 비교: {})",
                itemTime.getStartTime(),
                itemTime.getStartTime().isEqual(now) ? "일치 ✅" : "불일치");
            log.debug("      └─ 종료 시간: {}", itemTime.getEndTime());

            if (itemTime.getStartTime().isEqual(now)) {
                log.info("   ✅ 알림 발송 조건 충족! 아이템: '{}'", itemTime.getRoutineItemName());
                processRoutineItemStartNotification(schedule, user, itemTime);
            }

            if (itemTime.getEndTime().isAfter(now)) {
                allItemsCompleted = false;
            }
        }

        if (allItemsCompleted && !calculatedItems.isEmpty()) {
            CalculatedRoutineItemTime lastItem = calculatedItems.get(calculatedItems.size() - 1);
            if (lastItem.getEndTime().isBefore(now) || lastItem.getEndTime().isEqual(now)) {
                log.info("   🏁 모든 루틴 아이템 완료 - 스케줄 완료 처리");
                markScheduleAsCompleted(schedule);
            }
        } else if (calculatedItems.isEmpty() && isScheduleCompleted(schedule, now)) {
            markScheduleAsCompleted(schedule);
        }

        log.info("✅ [ScheduleNotificationService] 루틴 아이템 알림 처리 완료");
    }

    // 개별 아이템 루틴 처리
    private void processRoutineItemStartNotification(Schedule schedule, User user, CalculatedRoutineItemTime itemTime) {
        // 스케줄 ID + 루틴 아이템 ID 조합으로 중복 체크
        Optional<Notification> existingNotification = notificationRepository
                .findByUserAndScheduleIdAndRelatedIdAndNotificationType(
                        user, schedule.getId(), itemTime.getRoutineItemId(), NOTIFICATION_TYPE_ROUTINE_ITEM_START);

        if (existingNotification.isEmpty()) {
            String title = itemTime.getRoutineItemName();
            String body = String.format("'%s' 일정의 [%s] 할 시간입니다!", schedule.getTitle(), itemTime.getRoutineItemName());

            Map<String, String> data = createRoutineItemNotificationData(schedule, itemTime);
            sendRoutineItemNotification(user, schedule, title, body, data, itemTime.getRoutineItemId());
        } else {
            log.debug("루틴 아이템 알림이 이미 전송되었습니다. Schedule ID: {}, Routine Item ID: {}", 
                    schedule.getId(), itemTime.getRoutineItemId());
        }
    }

    // 스케줄 시작될 때 루틴의 첫 아이템 검사
    private void processRoutineItemsAtScheduleStart(Schedule schedule, User user, LocalDateTime now) {
        if (schedule.getRoutineId() == null || !user.isNotifyRoutineProgress()) {
            return;
        }

        List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
                schedule.getRoutineId(), schedule.getStartTime());

        for (CalculatedRoutineItemTime itemTime : calculatedItems) {
            if (itemTime.getStartTime().isEqual(now)) {
                processRoutineItemStartNotification(schedule, user, itemTime);
            }
        }
    }

    // 스케줄 시작 알림 본문 생성
    private String createScheduleStartBody(Schedule schedule) {
        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append(String.format("'%s'이(가) 지금 시작돼요!", schedule.getTitle()));

        if (schedule.getLocation() != null && !schedule.getLocation().trim().isEmpty()) {
            bodyBuilder.append(String.format("\n%s: %s", arrivalLocationPrefix, schedule.getLocation()));
        }

        if (schedule.getStartLocation() != null && !schedule.getStartLocation().trim().isEmpty()) {
            bodyBuilder.append(String.format("\n%s: %s", departureLocationPrefix, schedule.getStartLocation()));
        }

        return bodyBuilder.toString();
    }

    // 기본 알림 데이터 생성
    private Map<String, String> createNotificationData(String scheduleId, String type) {
        Map<String, String> data = new HashMap<>();
        data.put("scheduleId", scheduleId);
        data.put("type", type);
        return data;
    }

    // 루틴 아이템 알림 데이터 생성
    private Map<String, String> createRoutineItemNotificationData(Schedule schedule, CalculatedRoutineItemTime itemTime) {
        Map<String, String> data = new HashMap<>();
        data.put("scheduleId", schedule.getId().toString());
        data.put("routineId", itemTime.getRoutineId().toString());
        data.put("routineItemId", itemTime.getRoutineItemId().toString());
        data.put("type", NOTIFICATION_TYPE_ROUTINE_ITEM_START);
        return data;
    }

    // 알림 전송 및 DB 저장
    private void sendAndSaveNotification(User user, String title, String body, Map<String, String> data, Long relatedId, String notificationType) {
        try {
            if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                log.warn("{} 알림 전송 시도: 사용자 ID {}의 FCM 토큰이 없습니다.", notificationType, user.getId());
                return;
            }

            // 동시성 제어: 알림 전송 전에 다시 한번 중복 확인
            synchronized (this) {
                Optional<Notification> existingCheck = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, relatedId, notificationType);

                if (existingCheck.isPresent()) {
                    log.info("중복 알림 방지: 이미 존재하는 알림 - 사용자 ID: {}, 관련 ID: {}, 타입: {}",
                            user.getId(), relatedId, notificationType);
                    return;
                }

                // DB에 먼저 저장
                Notification notification = Notification.builder()
                        .user(user)
                        .title(title)
                        .body(body)
                        .relatedId(relatedId)
                        .notificationType(notificationType)
                        .build();
                notificationRepository.save(notification);
                log.info("{} 알림 DB 저장 완료: 알림 ID {}", notificationType, notification.getId());

                // FCM 전송
                fcmService.sendMessageToToken(user.getFcmToken(), title, body, data);
                log.info("{} 알림 전송 성공: 사용자 ID {}, 관련 ID {}", notificationType, user.getId(), relatedId);
            }

        } catch (Exception e) {
            log.error("{} 알림 전송/저장 실패: 사용자 ID {}, 관련 ID {}. 오류: {}",
                    notificationType, user.getId(), relatedId, e.getMessage(), e);
        }
    }

    // 루틴 아이템 전용 알림 전송 (scheduleId 포함)
    private void sendRoutineItemNotification(User user, Schedule schedule, String title, String body, 
                                            Map<String, String> data, Long routineItemId) {
        try {
            if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                log.warn("루틴 아이템 알림 전송 시도: 사용자 ID {}의 FCM 토큰이 없습니다.", user.getId());
                return;
            }

            // 동시성 제어: 알림 전송 전에 다시 한번 중복 확인
            synchronized (this) {
                Optional<Notification> existingCheck = notificationRepository
                        .findByUserAndScheduleIdAndRelatedIdAndNotificationType(
                                user, schedule.getId(), routineItemId, NOTIFICATION_TYPE_ROUTINE_ITEM_START);

                if (existingCheck.isPresent()) {
                    log.info("중복 알림 방지: 이미 존재하는 루틴 아이템 알림 - 사용자 ID: {}, 스케줄 ID: {}, 루틴 아이템 ID: {}",
                            user.getId(), schedule.getId(), routineItemId);
                    return;
                }

                // DB에 먼저 저장 (scheduleId 포함)
                Notification notification = Notification.builder()
                        .user(user)
                        .title(title)
                        .body(body)
                        .relatedId(routineItemId)
                        .scheduleId(schedule.getId())
                        .notificationType(NOTIFICATION_TYPE_ROUTINE_ITEM_START)
                        .build();
                notificationRepository.save(notification);
                log.info("루틴 아이템 알림 DB 저장 완료: 알림 ID {}, 스케줄 ID: {}, 루틴 아이템 ID: {}", 
                        notification.getId(), schedule.getId(), routineItemId);

                // FCM 전송
                fcmService.sendMessageToToken(user.getFcmToken(), title, body, data);
                log.info("루틴 아이템 알림 전송 성공: 사용자 ID {}, 스케줄 ID: {}, 루틴 아이템 ID: {}", 
                        user.getId(), schedule.getId(), routineItemId);
            }

        } catch (Exception e) {
            log.error("루틴 아이템 알림 전송/저장 실패: 사용자 ID {}, 스케줄 ID: {}, 루틴 아이템 ID: {}. 오류: {}",
                    user.getId(), schedule.getId(), routineItemId, e.getMessage(), e);
        }
    }

    // 사용자 검증
    private boolean isValidNotificationUser(User user) {
        return user != null &&
               user.getFcmToken() != null &&
               !user.getFcmToken().isEmpty() &&
               user.isNotificationsEnabled();
    }

    // 스케줄이 완료되었는지 확인
    private boolean isScheduleCompleted(Schedule schedule, LocalDateTime now) {
        return schedule.getEndTime() != null &&
               (schedule.getEndTime().isBefore(now) || schedule.getEndTime().isEqual(now));
    }

    // 스케줄을 IN_PROGRESS 상태로 변경
    private void markScheduleAsInProgress(Schedule schedule) {
        schedule.setStatus(Schedule.ScheduleStatus.IN_PROGRESS);
        scheduleRepository.save(schedule);
        log.info("스케줄 ID {} 상태를 IN_PROGRESS로 변경", schedule.getId());
    }

    // 스케줄을 COMPLETED 상태로 변경
    private void markScheduleAsCompleted(Schedule schedule) {
        schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
        scheduleRepository.save(schedule);
        log.info("스케줄 ID {} 상태를 COMPLETED로 변경", schedule.getId());
    }

    /**
     * 지연 등록된 루틴 아이템에 대한 알림 전송
     * @param schedule 일정 정보
     * @param user 사용자 정보
     * @param routineItemName 현재 시간에 해당하는 루틴 아이템 이름
     */
    public void sendDelayedRoutineItemNotification(Schedule schedule, User user, String routineItemName) {
        try {
            String fcmToken = user.getFcmToken();
            if (fcmToken == null || fcmToken.isEmpty()) {
                log.warn("사용자 FCM 토큰이 없습니다. User ID: {}", user.getId());
                return;
            }

            String title = "늦은 일정 등록";
            String body = String.format("이미 시작 시간이 지났는데, '%s'을(를) 완료하셨나요?", routineItemName);

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("routineItemName", routineItemName);
            data.put("type", "delayed_routine_item");

            Notification notification = Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .relatedId(schedule.getId())
                    .notificationType("delayed_routine_item")
                    .build();

            fcmService.sendMessageToToken(fcmToken, title, body, data);
            notificationRepository.save(notification);
            log.info("지연 루틴 아이템 알림 전송 완료 - User ID: {}, Schedule ID: {}, Item: {}",
                    user.getId(), schedule.getId(), routineItemName);

        } catch (Exception e) {
            log.error("지연 루틴 아이템 알림 전송 실패 - User ID: {}, Schedule ID: {}, Item: {}",
                    user.getId(), schedule.getId(), routineItemName, e);
        }
    }

    /**
     * 비대면 일정 판별 (category가 REMOTE, ONLINE 등)
     */
    private boolean isRemoteSchedule(Schedule schedule) {
        if (schedule.getCategory() == null) return false;
        String category = schedule.getCategory().name().toLowerCase();
        return category.contains("remote") ||
               category.contains("online") ||
               category.contains("비대면") ||
               category.contains("화상") ||
               category.contains("온라인");
    }

    /**
     * 루틴 시작 1시간 전 알림 전송 (출발지와 도착지 날씨 정보 포함)
     * @param schedule 일정 정보
     * @param user 사용자 정보
     */
    private void sendRoutineStartReminderWithWeather(Schedule schedule, User user) {
        try {
            String title = "루틴 시작 알림";
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append(String.format("'%s' 루틴이 1시간 후 시작됩니다!", schedule.getTitle()));

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("routineId", schedule.getRoutineId().toString());
            data.put("type", NOTIFICATION_TYPE_ROUTINE_START_REMINDER);
            data.put("startTime", schedule.getStartTime().toString());

            // 비대면 일정이면 날씨 정보 없이 알림 전송
            if (isRemoteSchedule(schedule)) {
                log.info("🏠 [ScheduleNotificationService] 비대면 일정 감지 - Schedule ID: {}, Category: {}",
                        schedule.getId(), schedule.getCategory());

                bodyBuilder.append("\n\n💻 온라인 일정이므로 편안한 곳에서 참여하세요!");
                data.put("hasWeather", "false");
                data.put("isRemote", "true");

                sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                    schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

                log.info("✅ [ScheduleNotificationService] 비대면 일정 1시간 전 알림 전송 완료 - User ID: {}, Schedule ID: {}",
                        user.getId(), schedule.getId());
                return;
            }

            // 대면 일정 - 출발지와 도착지 날씨 정보 조회
            log.info("🚶 [ScheduleNotificationService] 대면 일정 - 날씨 정보 조회 시작 - Schedule ID: {}", schedule.getId());
            fetchBothLocationWeathersForNotification(schedule, bodyBuilder, data)
                .subscribe(
                    weatherData -> {
                        // 알림 전송
                        sendAndSaveNotification(user, title, bodyBuilder.toString(), weatherData,
                            schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

                        log.info("✅ [ScheduleNotificationService] 대면 일정 1시간 전 알림 전송 완료 (날씨 포함) - User ID: {}, Schedule ID: {}",
                            user.getId(), schedule.getId());
                    },
                    error -> {
                        log.warn("⚠️ [ScheduleNotificationService] 날씨 정보 조회 실패 - Schedule ID: {}, 에러: {}",
                            schedule.getId(), error.getMessage());

                        // 날씨 정보 없이 알림 전송
                        data.put("hasWeather", "false");
                        sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                            schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

                        log.info("✅ [ScheduleNotificationService] 대면 일정 1시간 전 알림 전송 완료 (날씨 정보 없음) - User ID: {}, Schedule ID: {}",
                            user.getId(), schedule.getId());
                    }
                );

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 루틴 시작 1시간 전 알림 전송 실패 - User ID: {}, Schedule ID: {}, 에러: {}",
                user.getId(), schedule.getId(), e.getMessage(), e);
        }
    }

    /**
     * 알림용 출발지와 도착지 날씨 정보를 조회합니다.
     */
    private Mono<Map<String, String>> fetchBothLocationWeathersForNotification(
            Schedule schedule, StringBuilder bodyBuilder, Map<String, String> data) {

        // 출발지 날씨 조회
        Mono<WeatherResponse> startLocationWeatherMono =
            (schedule.getStartX() != null && schedule.getStartY() != null) ?
                weatherApiService.getCurrentWeather(schedule.getStartY(), schedule.getStartX())
                        .onErrorResume(error -> Mono.empty()) : Mono.empty();

        // 도착지 날씨 조회
        Mono<WeatherResponse> destinationWeatherMono =
            (schedule.getDestinationX() != null && schedule.getDestinationY() != null) ?
                weatherApiService.getCurrentWeather(schedule.getDestinationY(), schedule.getDestinationX())
                    .onErrorResume(error -> Mono.empty()) : Mono.empty();

        return Mono.zip(startLocationWeatherMono, destinationWeatherMono)
                .map(tuple -> {
                    WeatherResponse startWeather = tuple.getT1();
                    WeatherResponse destinationWeather = tuple.getT2();

                    // 악천후 체크 및 시간 조정
                    boolean isSevereWeather = checkAndHandleSevereWeather(schedule, startWeather, destinationWeather, bodyBuilder, data);

                    // 알림 메시지에 날씨 정보 추가
                    if (startWeather != null || destinationWeather != null) {
                        bodyBuilder.append("\n\n🌤️ 날씨 정보:");

                        if (startWeather != null && schedule.getStartLocation() != null) {
                            bodyBuilder.append(String.format("\n📍 %s: %s, %.1f°C",
                                schedule.getStartLocation(),
                                startWeather.getWeather().get(0).getDescription(),
                                startWeather.getMain().getTemp()));
                        }

                        if (destinationWeather != null && schedule.getLocation() != null) {
                            bodyBuilder.append(String.format("\n🎯 %s: %s, %.1f°C",
                                schedule.getLocation(),
                                destinationWeather.getWeather().get(0).getDescription(),
                                destinationWeather.getMain().getTemp()));
                        }
                    }

                    // FCM 데이터에 날씨 정보 추가
                    data.put("hasWeather", "true");

                    if (startWeather != null) {
                        data.put("startWeatherDescription", startWeather.getWeather().get(0).getDescription());
                        data.put("startTemperature", String.valueOf(startWeather.getMain().getTemp()));
                        data.put("startFeelsLike", String.valueOf(startWeather.getMain().getFeels_like()));
                        data.put("startHumidity", String.valueOf(startWeather.getMain().getHumidity()));
                        data.put("startWeatherIcon", startWeather.getWeather().get(0).getIcon());
                        data.put("startWeatherType", weatherApiService.determineWeatherType(startWeather));
                    }

                    if (destinationWeather != null) {
                        data.put("destWeatherDescription", destinationWeather.getWeather().get(0).getDescription());
                        data.put("destTemperature", String.valueOf(destinationWeather.getMain().getTemp()));
                        data.put("destFeelsLike", String.valueOf(destinationWeather.getMain().getFeels_like()));
                        data.put("destHumidity", String.valueOf(destinationWeather.getMain().getHumidity()));
                        data.put("destWeatherIcon", destinationWeather.getWeather().get(0).getIcon());
                        data.put("destWeatherType", weatherApiService.determineWeatherType(destinationWeather));
                    }

                    // 하위 호환성을 위해 기존 필드도 유지 (도착지 정보 사용)
                    if (destinationWeather != null) {
                        data.put("weatherDescription", destinationWeather.getWeather().get(0).getDescription());
                        data.put("temperature", String.valueOf(destinationWeather.getMain().getTemp()));
                        data.put("feelsLike", String.valueOf(destinationWeather.getMain().getFeels_like()));
                        data.put("humidity", String.valueOf(destinationWeather.getMain().getHumidity()));
                        data.put("weatherIcon", destinationWeather.getWeather().get(0).getIcon());
                        data.put("weatherType", weatherApiService.determineWeatherType(destinationWeather));
                    }

                    return data;
                })
                .onErrorReturn(data); // 오류 시 원본 data 반환
    }

    /**
     * 악천후 체크 및 일정 시간 조정 처리
     * @param schedule 스케줄 정보
     * @param startWeather 출발지 날씨
     * @param destinationWeather 도착지 날씨
     * @param bodyBuilder 알림 메시지 빌더
     * @param data 알림 데이터
     * @return 악천후 여부
     */
    private boolean checkAndHandleSevereWeather(Schedule schedule, WeatherResponse startWeather,
                                                 WeatherResponse destinationWeather,
                                                 StringBuilder bodyBuilder, Map<String, String> data) {
        boolean isSevereStart = startWeather != null && weatherApiService.isSevereWeather(startWeather);
        boolean isSevereDest = destinationWeather != null && weatherApiService.isSevereWeather(destinationWeather);

        if (isSevereStart || isSevereDest) {
            log.warn("⚠️ [ScheduleNotificationService] 악천후 감지 - Schedule ID: {}, 출발지 악천후: {}, 도착지 악천후: {}",
                    schedule.getId(), isSevereStart, isSevereDest);

            // 출발/도착 시간을 30분 앞당김
            LocalDateTime originalStartTime = schedule.getStartTime();
            LocalDateTime originalEndTime = schedule.getEndTime();
            LocalDateTime newStartTime = originalStartTime.minusMinutes(20);
            LocalDateTime newEndTime = originalEndTime.minusMinutes(20);

            schedule.setStartTime(newStartTime);
            schedule.setEndTime(newEndTime);
            scheduleRepository.save(schedule);

            log.info("🕐 [ScheduleNotificationService] 악천후로 인한 일정 시간 조정 완료 - Schedule ID: {}, " +
                    "원래 시작: {} -> 변경: {}, 원래 종료: {} -> 변경: {}",
                    schedule.getId(), originalStartTime, newStartTime, originalEndTime, newEndTime);

            // 악천후 알림 추가
            String weatherDesc = isSevereDest ?
                    weatherApiService.getSevereWeatherDescription(destinationWeather) :
                    weatherApiService.getSevereWeatherDescription(startWeather);

            bodyBuilder.append(String.format("\n\n⚠️ 악천후 경보 (%s)!", weatherDesc));
            bodyBuilder.append("\n날씨 때문에 늦을 수 있으니 출발 시간을 20분 앞당겼습니다.");
            bodyBuilder.append(String.format("\n새로운 출발 시간: %s",
                    newStartTime.toLocalTime().toString()));

            // 악천후 알림을 별도로 FCM 전송 및 DB 저장
            sendSevereWeatherNotification(schedule, schedule.getUser(), weatherDesc, newStartTime);

            // 데이터에 악천후 정보 추가
            data.put("isSevereWeather", "true");
            data.put("severeWeatherDescription", weatherDesc);
            data.put("originalStartTime", originalStartTime.toString());
            data.put("newStartTime", newStartTime.toString());
            data.put("originalEndTime", originalEndTime.toString());
            data.put("newEndTime", newEndTime.toString());

            return true;
        }

        data.put("isSevereWeather", "false");
        return false;
    }

    /**
     * 악천후 알림을 별도로 전송
     */
    private void sendSevereWeatherNotification(Schedule schedule, User user, String weatherDesc, LocalDateTime newStartTime) {
        try {
            String title = "⚠️ 악천후 알림";
            String body = String.format("'%s' 일정에 악천후(%s)가 예상됩니다.\n" +
                    "늦을 수 있으니 출발 시간을 30분 앞당겼습니다.\n" +
                    "새로운 출발 시간: %s",
                    schedule.getTitle(), weatherDesc, newStartTime.toLocalTime().toString());

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("type", "SEVERE_WEATHER_ALERT");
            data.put("weatherDescription", weatherDesc);
            data.put("newStartTime", newStartTime.toString());

            sendAndSaveNotification(user, title, body, data, schedule.getId(), "SEVERE_WEATHER_ALERT");

            log.info("✅ [ScheduleNotificationService] 악천후 별도 알림 전송 완료 - User ID: {}, Schedule ID: {}",
                    user.getId(), schedule.getId());

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 악천후 알림 전송 실패 - User ID: {}, Schedule ID: {}, 에러: {}",
                    user.getId(), schedule.getId(), e.getMessage(), e);
        }
    }
}
