package com.example.demo.service;

import com.example.demo.dto.routine.CalculatedRoutineItemTime;
import com.example.demo.dto.weather.WeatherResponse;
import com.example.demo.entity.fcm.Notification;
import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.user.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.ScheduleRepository;
import com.example.demo.repository.UserRepository;
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

    private static final String NOTIFICATION_TYPE_SCHEDULE_START = "SCHEDULE_START";
    private static final String NOTIFICATION_TYPE_ROUTINE_ITEM_START = "ROUTINE_ITEM_START";
    private static final String NOTIFICATION_TYPE_SUPPLIES_REMINDER = "SUPPLIES_REMINDER";
    private static final String NOTIFICATION_TYPE_ROUTINE_START_REMINDER = "ROUTINE_START_REMINDER";
    private final ScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;
    private final FCMService fcmService;
    private final RoutineService routineService;
    private final WeatherApiService weatherApiService;
    private final TransportService transportService;
    private final OdysseyTransitService odysseyTransitService;
    private final UserRepository userRepository;
    private final RoutineRepository routineRepository;

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

    @Scheduled(cron = "0 * * * * ?") // 매 분 0초에 실행
    public void sendScheduleAndRoutineNotifications() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        log.info("스케줄 및 루틴 알림 작업 실행: 현재 시간 {}", now);

        // 1. 루틴 시작 1시간 전 알림 처리
        processRoutineStartReminders(now);

        // 2. 준비물 알림 처리 (루틴 시작 N분 전)
        processSuppliesNotification(now);

        // 3. PENDING 스케줄 알림 처리 (준비물 제외)
        processPendingScheduleNotifications(now);

        // 4. IN_PROGRESS 루틴 아이템 시작 알림 처리
        processInProgressScheduleNotifications(now);
    }


    // 루틴 시작 1시간 전 알림 처리 (날씨 정보 포함)
    private void processRoutineStartReminders(LocalDateTime now) {
        // 루틴 소요 시간을 고려하여 약속 시간 검색 범위를 넓게 설정 (현재 시간 + 1시간 ~ 현재 시간 + 5시간)
        // 이는 루틴의 최대 소요 시간을 4시간으로 가정하는 것
        LocalDateTime searchStart = now.plusHours(1);
        LocalDateTime searchEnd = now.plusHours(5);

        log.info("🔍 [ScheduleNotificationService] 1시간 전 알림 처리 시작 - 현재시간: {}, 약속시간 검색범위: {} ~ {}",
                now, searchStart, searchEnd);

        // 검색 범위 내의 루틴이 포함된 PENDING 상태의 스케줄들 조회
        List<Schedule> candidateSchedules = scheduleRepository.findByStartTimeAndStatusAndRoutineIdNotNull(
                searchStart, searchEnd, Schedule.ScheduleStatus.PENDING);

        log.info("📋 [ScheduleNotificationService] 1시간 전 알림 후보 스케줄 {}개 발견", candidateSchedules.size());

        for (Schedule schedule : candidateSchedules) {
            try {
                // 각 스케줄의 실제 루틴 시작 시간 계산
                List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
                        schedule.getRoutineId(), schedule.getStartTime());

                if (calculatedItems.isEmpty()) {
                    continue;
                }

                LocalDateTime routineStartTime = calculatedItems.get(0).getStartTime();

                // 루틴 시작 시간이 정확히 1시간 후인지 확인
                if (routineStartTime.withSecond(0).withNano(0).isEqual(now.plusHours(1))) {
                    log.debug("🎯 [ScheduleNotificationService] 알림 대상 스케줄 찾음 - ID: {}, 제목: '{}', 약속시간: {}, 루틴시작시간: {}",
                            schedule.getId(), schedule.getTitle(), schedule.getStartTime(), routineStartTime);

                    User user = schedule.getUser();
                    if (!isValidNotificationUser(user) || !user.isNotifyRoutineProgress()) {
                        log.debug("⚠️ [ScheduleNotificationService] 사용자 알림 조건 미충족 - User ID: {}, FCM Token: {}, NotifyRoutineProgress: {}",
                                user.getId(),
                                (user.getFcmToken() != null ? "있음" : "없음"),
                                user.isNotifyRoutineProgress());
                        continue;
                    }

                    // 이미 알림을 보냈는지 확인
                    log.debug("🔍 [NotificationRepository] 중복 알림 확인 시도 - User ID: {}, Related ID: {}, Type: {}",
                            user.getId(), schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);
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
            } catch (Exception e) {
                log.error("❌ [ScheduleNotificationService] 루틴 시작 1시간 전 알림 처리 중 오류 발생 - Schedule ID: {}, 에러: {}",
                        schedule.getId(), e.getMessage(), e);
            }
        }

        log.info("✅ [ScheduleNotificationService] 1시간 전 알림 처리 완료");
    }

    // PENDING 상태의 스케줄들에 대한 알림 처리
    private void processPendingScheduleNotifications(LocalDateTime now) {
        LocalDateTime scheduleNotificationRangeEnd = now.plusMinutes(10);

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
                // 루틴 없는 스케줄의 완료 처리
                if (schedule.getRoutineId() == null && isScheduleCompleted(schedule, now)) {
                    markScheduleAsCompleted(schedule);
                }
                continue;
            }

            processRoutineItemNotifications(schedule, user, now);
        }
    }

    // 준비물 알림 (루틴 시작 N분 전 기준)

    /**
     * 준비물 알림 처리 (루틴 시작 N분 전)
     * processRoutineStartReminders와 유사한 로직으로 구현
     */

    private void processSuppliesNotification(LocalDateTime now) {
        // 조회 범위 설정 (현재 ~ 현재+150분)
        LocalDateTime searchStart = now;
        LocalDateTime searchEnd = now.plusMinutes(150);

        // PENDING 상태의 루틴이 있는 스케줄 조회
        List<Schedule> schedulesWithRoutine = scheduleRepository.findByStartTimeAndStatusAndRoutineIdNotNull(
                searchStart, searchEnd, Schedule.ScheduleStatus.PENDING);

        log.info("📦 [SuppliesNotification] 준비물 알림 체크 - 총 {}개 스케줄", schedulesWithRoutine.size());

        for (Schedule schedule : schedulesWithRoutine) {
            User user = schedule.getUser();

            // 사용자 유효성 검사
            if (!isValidNotificationUser(user)) {
                continue;
            }

            // 준비물 알림 비활성화 또는 준비물 없음
            if (!user.isNotifySupplies() ||
                    schedule.getSupplies() == null ||
                    schedule.getSupplies().trim().isEmpty()) {
                continue;
            }

            try {
            List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
                    schedule.getRoutineId(),
                    schedule.getStartTime()
            );

            // 루틴 아이템이 없으면 건너뜀
            if (calculatedItems == null || calculatedItems.isEmpty()) {
                log.debug("루틴 아이템이 없음 - 스케줄 ID: {}", schedule.getId());
                continue;
            }

            // 첫 번째 아이템의 시작 시간 = 루틴 시작 시간
            LocalDateTime routineStartTime = calculatedItems.get(0).getStartTime();

            // 준비물 알림 시간 = 루틴 시작 - N분
            LocalDateTime suppliesNotificationTime = routineStartTime.minusMinutes(suppliesNotificationMinutesBefore);

            log.info("📦 [준비물 알림 시간 계산] 스케줄 ID: {}", schedule.getId());
            log.info("   ├─ 현재 시간: {}", now);
            log.info("   ├─ 스케줄 시작: {}", schedule.getStartTime());
            log.info("   ├─ 루틴 시작: {}", routineStartTime);
            log.info("   ├─ 준비물 알림 시간: {}", suppliesNotificationTime);
            log.info("   ├─ N분 전: {}분", suppliesNotificationMinutesBefore);
            log.info("   └─ 시간 일치 여부: {}", suppliesNotificationTime.isEqual(now));

            // 알림 시간 도달 확인
            if (suppliesNotificationTime.isEqual(now)) {
                // ⭐ 알림 중복 체크 추가
                Optional<Notification> existingNotification = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);

                if (existingNotification.isEmpty()) {
                    log.info("📦 준비물 알림 전송 - 사용자: {}, 스케줄 ID: {}, 루틴 시작 {}분 전",
                            user.getEmail(), schedule.getId(), suppliesNotificationMinutesBefore);

                    String title = "준비물 알림";
                    String body = String.format("🎒 %s 준비물 체크하세요!\n일정 시작까지 %d분 남았습니다.",
                            schedule.getSupplies(),
                            suppliesNotificationMinutesBefore);

                    Map<String, String> data = new HashMap<>();
                    data.put("type", NOTIFICATION_TYPE_SUPPLIES_REMINDER);
                    data.put("scheduleId", String.valueOf(schedule.getId()));
                    data.put("routineId", String.valueOf(schedule.getRoutineId()));
                    data.put("supplies", schedule.getSupplies());
                    data.put("routineStartTime", routineStartTime.toString());
                    data.put("reminderMinutes", String.valueOf(suppliesNotificationMinutesBefore));

                    sendAndSaveNotification(user, title, body, data, schedule.getId(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);

                    log.info("✅ 준비물 알림 전송 완료 - 스케줄 ID: {}", schedule.getId());
                } else {
                    log.info("📦 준비물 알림 중복 감지 (이미 전송됨) - 스케줄 ID: {}", schedule.getId());
                }
            } else {
                log.debug("⏰ 준비물 알림 시간 아님 - 스케줄 ID: {}, 알림 시간: {}, 현재: {}",
                        schedule.getId(), suppliesNotificationTime, now);
            }

        } catch (Exception e) {
            log.error("준비물 알림 처리 중 오류 - 스케줄 ID: {}, 오류: {}",
                    schedule.getId(), e.getMessage(), e);
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

        // 모든 루틴 아이템이 완료되었는지 체크
        if (allItemsCompleted && !calculatedItems.isEmpty()) {
            CalculatedRoutineItemTime lastItem = calculatedItems.get(calculatedItems.size() - 1);
            if (lastItem.getEndTime().isBefore(now) || lastItem.getEndTime().isEqual(now)) {
                log.info("   🏁 모든 루틴 아이템 완료");

                // 스케줄의 실제 종료 시간을 확인하여 완료 처리
                if (isScheduleCompleted(schedule, now)) {
                    log.info("   ✅ 스케줄도 완료됨 - COMPLETED로 변경");
                    markScheduleAsCompleted(schedule);
                } else {
                    log.info("   ⏳ 스케줄은 아직 진행 중 - IN_PROGRESS 유지");
                    log.info("   └─ 스케줄 종료 시간: {}", schedule.getEndTime());
                }
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
    // ScheduleNotificationService.java

    private void sendAndSaveNotification(User user, String title, String body, Map<String, String> data, Long relatedId, String notificationType) {
        Notification savedNotification = null;
        Long userId = user.getId(); // 사용자 ID 미리 저장

        try {
            // DB 저장 로직 (synchronized 블록)
            synchronized (this) {
                Optional<Notification> existingCheck = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, relatedId, notificationType);
                if (existingCheck.isPresent()) {
                    log.info("💾 중복 알림 방지 - 이미 존재 (User: {}, RelatedID: {}, Type: {})", userId, relatedId, notificationType);
                    return;
                }
                Notification notification = Notification.builder()
                        .user(user) // DB 저장은 원래 User 객체 사용
                        .title(title)
                        .body(body)
                        .relatedId(relatedId)
                        .notificationType(notificationType)
                        .build();
                savedNotification = notificationRepository.save(notification);
                log.info("💾 {} 알림 DB 저장 완료 - ID: {}, User: {}, RelatedID: {}", notificationType, savedNotification.getId(), userId, relatedId);
            } // synchronized 종료

            User latestUser = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("알림 전송 시 사용자를 찾을 수 없음: ID " + userId));
            String latestFcmToken = latestUser.getFcmToken(); // 최신 토큰 가져오기

            // DB 저장 후 FCM 또는 WebSocket으로 전송
            try {
                // *** 최신 토큰(latestFcmToken)을 사용하여 전송 ***
                fcmService.sendNotificationToUser(latestUser.getEmail(), latestFcmToken, title, body, data);
                log.info("📤 {} 알림 전송 시도 완료 - User: {}, RelatedID: {}", notificationType, userId, relatedId);
            } catch (Exception e) {
                // sendNotificationToUser 내부에서 예외를 처리하고 로깅하므로, 여기서는 추가 로깅만
                log.error("❌ {} 알림 전송 중 오류 발생 (sendNotificationToUser 호출 후) - User: {}, RelatedID: {}",
                        notificationType, userId, relatedId, e);
            }

        } catch (Exception e) {
            log.error("❌ {} 알림 저장/처리 실패 - User: {}, RelatedID: {}, 오류: {}",
                    notificationType, userId, relatedId, e.getMessage(), e);
        }
    }

    // 루틴 아이템 전용 알림 전송 (scheduleId 포함)
    // ScheduleNotificationService.java

    private void sendRoutineItemNotification(User user, Schedule schedule, String title, String body,
                                             Map<String, String> data, Long routineItemId) {
        Notification savedNotification = null;
        Long userId = user.getId(); // 사용자 ID 미리 저장
        Long scheduleId = schedule.getId(); // 스케줄 ID 미리 저장

        try {
            // DB 저장 로직 (synchronized 블록)
            synchronized (this) {
                Optional<Notification> existingCheck = notificationRepository
                        .findByUserAndScheduleIdAndRelatedIdAndNotificationType(
                                user, scheduleId, routineItemId, NOTIFICATION_TYPE_ROUTINE_ITEM_START);
                if (existingCheck.isPresent()) {
                    log.info("💾 중복 알림 방지 - 이미 존재 (User: {}, Schedule: {}, Item: {})", userId, scheduleId, routineItemId);
                    return;
                }
                Notification notification = Notification.builder()
                        .user(user) // DB 저장은 원래 User 객체 사용
                        .title(title)
                        .body(body)
                        .relatedId(routineItemId)
                        .scheduleId(scheduleId)
                        .notificationType(NOTIFICATION_TYPE_ROUTINE_ITEM_START)
                        .build();
                savedNotification = notificationRepository.save(notification);
                log.info("💾 루틴 아이템 알림 DB 저장 완료 - ID: {}, Schedule: {}, Item: {}", savedNotification.getId(), scheduleId, routineItemId);
            } // synchronized 종료

            // *** [수정] FCM 전송 직전에 최신 User 정보 조회 ***
            User latestUser = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("루틴 알림 전송 시 사용자를 찾을 수 없음: ID " + userId));
            String latestFcmToken = latestUser.getFcmToken(); // 최신 토큰 가져오기

            // DB 저장 후 FCM 또는 WebSocket으로 전송
            try {
                // *** 최신 토큰(latestFcmToken)을 사용하여 전송 ***
                fcmService.sendNotificationToUser(latestUser.getEmail(), latestFcmToken, title, body, data);
                log.info("📤 루틴 아이템 알림 전송 시도 완료 - User: {}, Schedule: {}, Item: {}", userId, scheduleId, routineItemId);
            } catch (Exception e) {
                log.error("❌ 루틴 아이템 알림 전송 중 오류 발생 (sendNotificationToUser 호출 후) - User: {}, Schedule: {}, Item: {}",
                        userId, scheduleId, routineItemId, e);
            }

        } catch (Exception e) {
            log.error("❌ 루틴 아이템 알림 저장/처리 실패 - User: {}, Schedule: {}, Item: {}, 오류: {}",
                    userId, scheduleId, routineItemId, e.getMessage(), e);
        }
    }

    // 사용자 검증 - FCM 토큰이 없어도 알림 활성화되어 있으면 유효
    private boolean isValidNotificationUser(User user) {
        return user != null && user.isNotificationsEnabled();
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
     *
     * @param schedule        일정 정보
     * @param user            사용자 정보
     * @param routineItemName 현재 시간에 해당하는 루틴 아이템 이름
     */
    public void sendDelayedRoutineItemNotification(Schedule schedule, User user, String routineItemName) {
        Notification savedNotification = null;
        String notificationType = "delayed_routine_item";
        Long relatedId = schedule.getId();
        Long userId = user.getId(); // 사용자 ID 미리 저장

        try {
            String title = "늦은 일정 등록";
            String body = String.format("이미 시작 시간이 지났는데, '%s'을(를) 완료하셨나요?", routineItemName);
            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("routineItemName", routineItemName);
            data.put("type", notificationType);

            // DB 저장 로직 (synchronized 블록)
            synchronized (this) {
                Optional<Notification> existingCheck = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, relatedId, notificationType);
                if (existingCheck.isPresent()) {
                    log.info("💾 중복 알림 방지 - 이미 존재 (User: {}, RelatedID: {}, Type: {})", userId, relatedId, notificationType);
                    return;
                }
                Notification notification = Notification.builder()
                        .user(user) // DB 저장은 원래 User 객체 사용
                        .title(title)
                        .body(body)
                        .relatedId(relatedId)
                        .notificationType(notificationType)
                        .build();
                savedNotification = notificationRepository.save(notification);
                log.info("💾 지연 루틴 알림 DB 저장 완료 - ID: {}, User: {}, Schedule: {}", savedNotification.getId(), userId, relatedId);
            } // synchronized 종료

            // *** [수정] FCM 전송 직전에 최신 User 정보 조회 ***
            User latestUser = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("지연 루틴 알림 전송 시 사용자를 찾을 수 없음: ID " + userId));
            String latestFcmToken = latestUser.getFcmToken(); // 최신 토큰 가져오기

            // DB 저장 후 FCM 또는 WebSocket으로 전송
            try {
                // *** 최신 토큰(latestFcmToken)을 사용하여 전송 ***
                fcmService.sendNotificationToUser(latestUser.getEmail(), latestFcmToken, title, body, data);
                log.info("📤 지연 루틴 알림 전송 시도 완료 - User: {}, Schedule: {}", userId, relatedId);
            } catch (Exception e) {
                log.error("❌ 지연 루틴 알림 전송 중 오류 발생 (sendNotificationToUser 호출 후) - User: {}, Schedule: {}, 오류: {}",
                        userId, relatedId, e);
            }

        } catch (Exception e) {
            log.error("❌ 지연 루틴 알림 저장/처리 실패 - User: {}, Schedule: {}, 오류: {}",
                    userId, relatedId, e.getMessage(), e);
        }
    }

    /**
     * 날씨로 인한 스케줄 조정 (15분 앞당김 + 제목 플래그 + 우산 추가)
     *
     * @param schedule 조정할 스케줄
     * @return 변경 전 시작 시간
     */
    public LocalDateTime adjustScheduleForWeather(Schedule schedule) {
        LocalDateTime originalStartTime = schedule.getStartTime();
        LocalDateTime newStartTime = originalStartTime.minusMinutes(15);
        schedule.setStartTime(newStartTime);

        log.info("⏰ [ScheduleNotificationService] 날씨로 인한 시간 변경 - Schedule ID: {}, 기존: {}, 변경: {} (15분 앞당김)",
                schedule.getId(), originalStartTime, newStartTime);

        // 일정 제목에 [기상악화] 플래그 추가
        if (!schedule.getTitle().startsWith("[기상악화]") && !schedule.getTitle().startsWith("[교통체증]")) {
            schedule.setTitle("[기상악화] " + schedule.getTitle());
            log.info("📝 [ScheduleNotificationService] 일정 제목 변경 - Schedule ID: {}, 새 제목: '{}'",
                    schedule.getId(), schedule.getTitle());
        }

        // 준비물에 우산 추가
        String currentSupplies = schedule.getSupplies();
        if (currentSupplies == null || currentSupplies.trim().isEmpty()) {
            schedule.setSupplies("우산");
            log.info("☂️ [ScheduleNotificationService] 준비물 추가 - Schedule ID: {}, 준비물: '우산'",
                    schedule.getId());
        } else if (!currentSupplies.contains("우산")) {
            schedule.setSupplies(currentSupplies + ", 우산");
            log.info("☂️ [ScheduleNotificationService] 준비물 추가 - Schedule ID: {}, 기존: '{}', 변경: '{}'",
                    schedule.getId(), currentSupplies, schedule.getSupplies());
        }

        scheduleRepository.save(schedule);
        return originalStartTime;
    }

    /**
     * 교통 지연으로 인한 스케줄 조정 (지연 시간만큼 앞당김 + 제목 플래그)
     *
     * @param schedule     조정할 스케줄
     * @param delayMinutes 지연 시간(분)
     * @return 변경 전 시작 시간
     */
    public LocalDateTime adjustScheduleForTrafficDelay(Schedule schedule, int delayMinutes) {
        LocalDateTime originalStartTime = schedule.getStartTime();
        LocalDateTime newStartTime = originalStartTime.minusMinutes(delayMinutes);
        schedule.setStartTime(newStartTime);

        log.info("⏰ [ScheduleNotificationService] 교통 지연으로 인한 시간 변경 - Schedule ID: {}, 기존: {}, 변경: {} ({}분 앞당김)",
                schedule.getId(), originalStartTime, newStartTime, delayMinutes);

        // 일정 제목에 [교통체증] 플래그 추가
        if (!schedule.getTitle().startsWith("[교통체증]") && !schedule.getTitle().startsWith("[기상악화]")) {
            schedule.setTitle("[교통체증] " + schedule.getTitle());
            log.info("📝 [ScheduleNotificationService] 일정 제목 변경 - Schedule ID: {}, 새 제목: '{}'",
                    schedule.getId(), schedule.getTitle());
        }

        scheduleRepository.save(schedule);
        return originalStartTime;
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
    /**
     * 루틴 시작 1시간 전 알림 전송 (출발지와 도착지 날씨 정보 포함)
     *
     * @param schedule 일정 정보
     * @param user     사용자 정보
     */
    private void sendRoutineStartReminderWithWeather(Schedule schedule, User user) {
        try {
            String title = "루틴 시작 알림";
            StringBuilder bodyBuilder = new StringBuilder();

            // 현재 시간부터 스케줄 시작 시간까지 남은 시간 계산
            LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
            long minutesUntilScheduleStart = java.time.Duration.between(now, schedule.getStartTime()).toMinutes();

            bodyBuilder.append(String.format("잔소리 시작 1시간 전!\n약속 시간까지 %d분 남았습니다.", minutesUntilScheduleStart));

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("routineId", schedule.getRoutineId().toString());
            data.put("type", NOTIFICATION_TYPE_ROUTINE_START_REMINDER);
            data.put("startTime", schedule.getStartTime().toString());

            // 비대면 일정이면 날씨 및 교통 정보 체크 생략
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

            // 날씨 체크 (목적지 좌표가 있는 경우에만)
            checkAndHandleWeather(schedule, user, bodyBuilder, data);

            // 교통 지연 체크 (출발지와 도착지 좌표가 있는 경우에만)
            checkAndHandleTrafficDelay(schedule, user, bodyBuilder, data);

            // 1시간 전 알림 전송 (날씨 및 교통 지연 정보 포함)
            sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                    schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

            log.info("✅ [ScheduleNotificationService] 1시간 전 알림 전송 완료 - User ID: {}, Schedule ID: {}",
                    user.getId(), schedule.getId());

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 루틴 시작 1시간 전 알림 전송 실패 - User ID: {}, Schedule ID: {}, 에러: {}",
                    user.getId(), schedule.getId(), e.getMessage(), e);
        }
    }

    /**
     * 날씨 체크 및 알림 처리
     * 악천후(비, 눈, 천둥 등) 감지 시 15분 앞당기고 우산 추가
     */
    private void checkAndHandleWeather(Schedule schedule, User user, StringBuilder bodyBuilder, Map<String, String> data) {
        // 목적지 좌표가 없으면 체크하지 않음
        if (schedule.getDestinationX() == null || schedule.getDestinationY() == null) {
            log.info("📍 [ScheduleNotificationService] 목적지 좌표 없음 - 날씨 체크 생략 - Schedule ID: {}", schedule.getId());
            return;
        }

        try {
            log.info("🌦️ [ScheduleNotificationService] 날씨 체크 시작 - Schedule ID: {}", schedule.getId());

            // 목적지 날씨 조회
            WeatherResponse weatherResponse = weatherApiService.getCurrentWeather(
                            schedule.getDestinationY(),
                            schedule.getDestinationX())
                    .block();

            if (weatherResponse == null) {
                log.warn("⚠️ [ScheduleNotificationService] 날씨 정보 조회 실패 - Schedule ID: {}", schedule.getId());
                return;
            }

            // 악천후 여부 확인 (비, 눈, 천둥, 폭풍 - 이슬비 제외)
            boolean isSevereWeather = weatherApiService.isSevereWeather(weatherResponse);

            if (isSevereWeather) {
                String weatherDesc = weatherApiService.getSevereWeatherDescription(weatherResponse);
                log.warn("🌧️ [ScheduleNotificationService] 악천후 감지 - Schedule ID: {}, 날씨: {}",
                        schedule.getId(), weatherDesc);

                // 일정 시작 시간 15분 앞당기기
                LocalDateTime originalStartTime = schedule.getStartTime();
                LocalDateTime newStartTime = originalStartTime.minusMinutes(15);
                schedule.setStartTime(newStartTime);

                log.info("⏰ [ScheduleNotificationService] 날씨로 인한 시간 변경 - Schedule ID: {}, 기존: {}, 변경: {} (15분 앞당김)",
                        schedule.getId(), originalStartTime, newStartTime);

                // 일정 제목에 [기상악화] 플래그 추가
                if (!schedule.getTitle().startsWith("[기상악화]") && !schedule.getTitle().startsWith("[교통체증]")) {
                    schedule.setTitle("[기상악화] " + schedule.getTitle());
                    log.info("📝 [ScheduleNotificationService] 일정 제목 변경 - Schedule ID: {}, 새 제목: '{}'",
                            schedule.getId(), schedule.getTitle());
                }

                // 준비물에 우산 추가
                String currentSupplies = schedule.getSupplies();
                if (currentSupplies == null || currentSupplies.trim().isEmpty()) {
                    schedule.setSupplies("우산");
                    log.info("☂️ [ScheduleNotificationService] 준비물 추가 - Schedule ID: {}, 준비물: '우산'",
                            schedule.getId());
                } else if (!currentSupplies.contains("우산")) {
                    schedule.setSupplies(currentSupplies + ", 우산");
                    log.info("☂️ [ScheduleNotificationService] 준비물 추가 - Schedule ID: {}, 기존: '{}', 변경: '{}'",
                            schedule.getId(), currentSupplies, schedule.getSupplies());
                }

                scheduleRepository.save(schedule);

                // 별도 날씨 알림 전송
                sendWeatherAlertNotification(schedule, user, weatherDesc);

                // 메시지에 날씨 정보 추가
                bodyBuilder.append(String.format("\n\n🌧️ 기상 악화 경보!\n날씨: %s\n⏰ 출발 시간이 15분 앞당겨졌습니다!\n☂️ 우산을 챙기세요!",
                        weatherDesc));

                // 데이터에 날씨 정보 추가
                data.put("hasSevereWeather", "true");
                data.put("weatherDescription", weatherDesc);
                data.put("originalStartTime", originalStartTime.toString());
                data.put("newStartTime", newStartTime.toString());
            } else {
                log.info("✅ [ScheduleNotificationService] 날씨 양호 - Schedule ID: {}", schedule.getId());
                data.put("hasSevereWeather", "false");
            }

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 날씨 체크 실패 - Schedule ID: {}, 에러: {}",
                    schedule.getId(), e.getMessage(), e);
            data.put("hasSevereWeather", "false");
        }
    }

    /**
     * 날씨 알림 별도 전송
     */
    public void sendWeatherAlertNotification(Schedule schedule, User user, String weatherDescription) {
        try {
            String title = "날씨 알림";
            String body = "날씨가 안좋아요!\n조금 일찍 나가볼까요?\n알람 시작 45분 전!";

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("type", "SEVERE_WEATHER_ALERT");
            data.put("weatherDescription", weatherDescription);
            data.put("severeWeatherDescription", weatherDescription);
            data.put("newStartTime", schedule.getStartTime().toString());
            data.put("originalStartTime", schedule.getStartTime().plusMinutes(15).toString());

            sendAndSaveNotification(user, title, body, data, schedule.getId(), "SEVERE_WEATHER_ALERT");

            log.info("✅ [ScheduleNotificationService] 날씨 알림 전송 완료 - User ID: {}, Schedule ID: {}, 날씨: {}",
                    user.getId(), schedule.getId(), weatherDescription);

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 날씨 알림 전송 실패 - User ID: {}, Schedule ID: {}, 에러: {}",
                    user.getId(), schedule.getId(), e.getMessage(), e);
        }
    }

    /**
     * 교통 지연 체크 및 알림 처리
     * 원본 교통 시간과 현재 교통 시간을 비교하여 15분 이상 차이나면 알림 전송
     */
    private void checkAndHandleTrafficDelay(Schedule schedule, User user, StringBuilder bodyBuilder, Map<String, String> data) {
        // 좌표 정보가 없으면 체크하지 않음
        if (schedule.getStartX() == null || schedule.getStartY() == null ||
                schedule.getDestinationX() == null || schedule.getDestinationY() == null) {
            log.info("📍 [ScheduleNotificationService] 좌표 정보 없음 - 교통 지연 체크 생략 - Schedule ID: {}", schedule.getId());
            return;
        }

        try {
            log.info("🚗 [ScheduleNotificationService] 교통 지연 체크 시작 - Schedule ID: {}", schedule.getId());

            // 1. 자차 시간 재확인 (TMAP API)
            Integer currentDrivingTime = transportService.calculateDrivingTimeInternal(
                    createTransportRequest(schedule));

            // 2. 대중교통 시간 재확인 (오디세이 API)
            Integer currentTransitTime = odysseyTransitService.getTransitTime(
                    schedule.getStartX(), schedule.getStartY(),
                    schedule.getDestinationX(), schedule.getDestinationY());

            boolean hasTrafficDelay = false;
            int maxDelay = 0;
            String delayType = "";

            // 자차 지연 체크
            if (schedule.getOriginalDrivingTime() != null && currentDrivingTime != null) {
                int drivingDelay = currentDrivingTime - schedule.getOriginalDrivingTime();
                log.info("🚗 [ScheduleNotificationService] 자차 시간 비교 - 원본: {}분, 현재: {}분, 차이: {}분",
                        schedule.getOriginalDrivingTime(), currentDrivingTime, drivingDelay);

                if (drivingDelay >= 15) {
                    hasTrafficDelay = true;
                    maxDelay = Math.max(maxDelay, drivingDelay);
                    delayType = "자차";
                }
            }

            // 대중교통 지연 체크
            if (schedule.getOriginalTransitTime() != null && currentTransitTime != null) {
                int transitDelay = currentTransitTime - schedule.getOriginalTransitTime();
                log.info("🚇 [ScheduleNotificationService] 대중교통 시간 비교 - 원본: {}분, 현재: {}분, 차이: {}분",
                        schedule.getOriginalTransitTime(), currentTransitTime, transitDelay);

                if (transitDelay >= 15) {
                    hasTrafficDelay = true;
                    if (transitDelay > maxDelay) {
                        maxDelay = transitDelay;
                        delayType = "대중교통";
                    }
                }
            }

            // 교통 지연 감지 시 처리
            if (hasTrafficDelay) {
                log.warn("🚨 [ScheduleNotificationService] 교통 지연 감지 - Schedule ID: {}, 지연: {}분 ({})",
                        schedule.getId(), maxDelay, delayType);

                // 일정 시작 시간 앞당기기 (지연 시간만큼)
                LocalDateTime originalStartTime = schedule.getStartTime();
                LocalDateTime newStartTime = originalStartTime.minusMinutes(maxDelay);
                schedule.setStartTime(newStartTime);

                log.info("⏰ [ScheduleNotificationService] 일정 시작 시간 변경 - Schedule ID: {}, 기존: {}, 변경: {} ({}분 앞당김)",
                        schedule.getId(), originalStartTime, newStartTime, maxDelay);

                // 일정 제목에 [교통체증] 표시 추가
                if (!schedule.getTitle().startsWith("[교통체증]") && !schedule.getTitle().startsWith("[기상악화]")) {
                    schedule.setTitle("[교통체증] " + schedule.getTitle());
                    log.info("📝 [ScheduleNotificationService] 일정 제목 변경 - Schedule ID: {}, 새 제목: '{}'",
                            schedule.getId(), schedule.getTitle());
                }

                scheduleRepository.save(schedule);

                // 별도 교통 지연 알림 전송
                sendTrafficDelayNotification(schedule, user, delayType, maxDelay);

                // 메시지에 교통 지연 정보 추가
                bodyBuilder.append(String.format("\n\n🚦 교통 지연 경보!\n%s 이동 시간이 평소보다 %d분 더 걸립니다.\n" +
                                "⏰ 출발 시간이 %d분 앞당겨졌습니다!",
                        delayType, maxDelay, maxDelay));

                // 데이터에 교통 지연 정보 추가
                data.put("hasTrafficDelay", "true");
                data.put("delayType", delayType);
                data.put("delayMinutes", String.valueOf(maxDelay));
                data.put("originalStartTime", originalStartTime.toString());
                data.put("newStartTime", newStartTime.toString());
            } else {
                log.info("✅ [ScheduleNotificationService] 교통 지연 없음 - Schedule ID: {}", schedule.getId());
                data.put("hasTrafficDelay", "false");
            }

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 교통 지연 체크 실패 - Schedule ID: {}, 에러: {}",
                    schedule.getId(), e.getMessage(), e);
            data.put("hasTrafficDelay", "false");
        }
    }

    /**
     * 교통 지연 알림 별도 전송
     */
    public void sendTrafficDelayNotification(Schedule schedule, User user, String delayType, int delayMinutes) {
        try {
            String title = "교통 알림";
            String body = String.format("교통 상황이 안 좋아요!\n조금 일찍 나가볼까요?\n알람 시작 %d분 전!", 60 - delayMinutes);

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("type", "TRAFFIC_DELAY_ALERT");
            data.put("delayType", delayType);
            data.put("delayMinutes", String.valueOf(delayMinutes));
            data.put("delayReason", delayType + " 교통 지연 (" + delayMinutes + "분)");
            data.put("newStartTime", schedule.getStartTime().toString());
            data.put("originalStartTime", schedule.getStartTime().plusMinutes(delayMinutes).toString());

            sendAndSaveNotification(user, title, body, data, schedule.getId(), "TRAFFIC_DELAY_ALERT");

            log.info("✅ [ScheduleNotificationService] 교통 지연 별도 알림 전송 완료 - User ID: {}, Schedule ID: {}, 지연: {}분",
                    user.getId(), schedule.getId(), delayMinutes);

        } catch (Exception e) {
            log.error("❌ [ScheduleNotificationService] 교통 지연 알림 전송 실패 - User ID: {}, Schedule ID: {}, 에러: {}",
                    user.getId(), schedule.getId(), e.getMessage(), e);
        }
    }

    /**
     * TransportTimeRequest 객체 생성 헬퍼 메서드
     */
    private com.example.demo.dto.transport.TransportTimeRequest createTransportRequest(Schedule schedule) {
        com.example.demo.dto.transport.TransportTimeRequest request =
                new com.example.demo.dto.transport.TransportTimeRequest();
        request.setStartX(schedule.getStartX());
        request.setStartY(schedule.getStartY());
        request.setEndX(schedule.getDestinationX());
        request.setEndY(schedule.getDestinationY());
        request.setRemoteEvent(isRemoteSchedule(schedule));
        return request;
    }
}