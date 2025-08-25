package com.example.demo.service;

import com.example.demo.dto.routine.CalculatedRoutineItemTime;
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
    private final WeatherApiService weatherApiService; // 날씨 서비스 추가

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
    private static final String NOTIFICATION_TYPE_ROUTINE_START_REMINDER = "ROUTINE_START_REMINDER"; // 루틴 시작 1시간 전 알림 타입 추가

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

        // 1시간 후 시작되는 루틴이 포함된 PENDING 상태의 스케줄들 조회
        List<Schedule> upcomingRoutineSchedules = scheduleRepository.findByStartTimeAndStatusAndRoutineIdNotNull(
                oneHourLater, Schedule.ScheduleStatus.PENDING);

        for (Schedule schedule : upcomingRoutineSchedules) {
            User user = schedule.getUser();
            if (!isValidNotificationUser(user) || !user.isNotifyRoutineProgress()) {
                continue;
            }

            // 이미 알림을 보냈는지 확인
            Optional<Notification> existingNotification = notificationRepository
                    .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

            if (existingNotification.isEmpty()) {
                sendRoutineStartReminderWithWeather(schedule, user, now);
            }
        }
    }

    // PENDING 상태의 스케줄들에 대한 알림 처리 + 준비물 알림 (설정된 시간 전) + 스케줄 시작 알림 (정각)

    private void processPendingScheduleNotifications(LocalDateTime now) {
        LocalDateTime scheduleNotificationRangeEnd = now.plusMinutes(5);

        List<Schedule> pendingSchedules = scheduleRepository.findByStartTimeBetweenAndStatusAndUserFcmTokenIsNotNull(
                now,
                scheduleNotificationRangeEnd.plusMinutes(suppliesNotificationMinutesBefore),
                Schedule.ScheduleStatus.PENDING
        );

        for (Schedule schedule : pendingSchedules) {
            User user = schedule.getUser();
            if (!isValidNotificationUser(user)) {
                continue;
            }

            // 준비물 알림 처리
            processSuppliesNotification(schedule, user, now);

            // 스케줄 시작 알림 처리
            processScheduleStartNotification(schedule, user, now);
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

    // 스케쥴 알림 처리
    private void processScheduleStartNotification(Schedule schedule, User user, LocalDateTime now) {
        if (user.isNotifyNextSchedule() && schedule.getStartTime().isEqual(now)) {
            Optional<Notification> existingNotification = notificationRepository
                    .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_SCHEDULE_START);

            if (existingNotification.isEmpty()) {
                String title = scheduleStartTitle;
                String body = createScheduleStartBody(schedule);

                Map<String, String> data = createNotificationData(schedule.getId().toString(), NOTIFICATION_TYPE_SCHEDULE_START);
                sendAndSaveNotification(user, title, body, data, schedule.getId(), NOTIFICATION_TYPE_SCHEDULE_START);
                // 기존에 각 루틴 첫 아이템이 노티에 나오지 못했던 이유 -> processPendingScheduleNotifications 이후에 호출 -> 지연때문에
                processRoutineItemsAtScheduleStart(schedule,user,now);

                markScheduleAsInProgress(schedule);
            } else {
                // 이미 알림이 갔지만 PENDING 상태라면 IN_PROGRESS로 변경
                if (schedule.getStatus() == Schedule.ScheduleStatus.PENDING) {
                    markScheduleAsInProgress(schedule);
                }
            }
        }
    }

    // 루틴 아이템 알림 처리
    private void processRoutineItemNotifications(Schedule schedule, User user, LocalDateTime now) {
        List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
                schedule.getRoutineId(), schedule.getStartTime());
        boolean allItemsCompleted = true;

        for (CalculatedRoutineItemTime itemTime : calculatedItems) {
            if (itemTime.getStartTime().isEqual(now)) {
                processRoutineItemStartNotification(schedule, user, itemTime);
            }

            if (itemTime.getEndTime().isAfter(now)) {
                allItemsCompleted = false;
            }
        }

        // 모든 루틴 아이템 완료 시 스케줄 완료 처리
        if (allItemsCompleted && !calculatedItems.isEmpty()) {
            CalculatedRoutineItemTime lastItem = calculatedItems.get(calculatedItems.size() - 1);
            if (lastItem.getEndTime().isBefore(now) || lastItem.getEndTime().isEqual(now)) {
                markScheduleAsCompleted(schedule);
            }
        } else if (calculatedItems.isEmpty() && isScheduleCompleted(schedule, now)) {
            markScheduleAsCompleted(schedule);
        }
    }

    // 개별 아이템 루틴 처리
    private void processRoutineItemStartNotification(Schedule schedule, User user, CalculatedRoutineItemTime itemTime) {
        Optional<Notification> existingNotification = notificationRepository
                .findByUserAndRelatedIdAndNotificationType(user, itemTime.getRoutineItemId(), NOTIFICATION_TYPE_ROUTINE_ITEM_START);

        if (existingNotification.isEmpty()) {
            String title = itemTime.getRoutineItemName();
            String body = String.format("'%s' 일정의 [%s] 할 시간입니다!", schedule.getTitle(), itemTime.getRoutineItemName());

            Map<String, String> data = createRoutineItemNotificationData(schedule, itemTime);
            sendAndSaveNotification(user, title, body, data, itemTime.getRoutineItemId(), NOTIFICATION_TYPE_ROUTINE_ITEM_START);
        } else {
            log.info("루틴 아이템 ID {} 시작 알림이 이미 전송되었습니다. 건너뜁니다.", itemTime.getRoutineItemId());
        }
    }

    // 스케쥴 시작될 때 루틴의 첫 아이템 검사
    private void processRoutineItemsAtScheduleStart(Schedule schedule, User user, LocalDateTime now) {
    if (schedule.getRoutineId() == null || !user.isNotifyRoutineProgress()) {
        return; // 루틴이 없거나 루틴 알림 설정이 꺼져있으면 실행하지 않음
    }

    List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(
            schedule.getRoutineId(), schedule.getStartTime());

    for (CalculatedRoutineItemTime itemTime : calculatedItems) {
        // 스케줄 시작 시간과 루틴 아이템 시작 시간이 같은 경우에만 알림 처리
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

            fcmService.sendMessageToToken(user.getFcmToken(), title, body, data);
            log.info("{} 알림 전송 성공: 사용자 ID {}, 관련 ID {}", notificationType, user.getId(), relatedId);

            Notification notification = Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .relatedId(relatedId)
                    .notificationType(notificationType)
                    .build();
            notificationRepository.save(notification);
            log.info("{} 알림 DB 저장 완료: 알림 ID {}", notificationType, notification.getId());

        } catch (Exception e) {
            log.error("{} 알림 전송/저장 실패: 사용자 ID {}, 관련 ID {}. 오류: {}",
                    notificationType, user.getId(), relatedId, e.getMessage(), e);
        }
    }

    //사용자 검증
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

            String title = "일정 등록 완료";
            String body = String.format("이미 시작 시간이 지났는데, '%s'을(를) 완료하셨나요?", routineItemName);

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("routineItemName", routineItemName);
            data.put("type", "delayed_routine_item");

            fcmService.sendMessageToToken(fcmToken, title, body, data);
            log.info("지연 루틴 아이템 알림 전송 완료 - User ID: {}, Schedule ID: {}, Item: {}",
                    user.getId(), schedule.getId(), routineItemName);

        } catch (Exception e) {
            log.error("지연 루틴 아이템 알림 전송 실패 - User ID: {}, Schedule ID: {}, Item: {}",
                    user.getId(), schedule.getId(), routineItemName, e);
        }
    }

    /**
     * 루틴 시작 1시간 전 알림 전송 (날씨 정보 포함)
     * @param schedule 일정 정보
     * @param user 사용자 정보
     * @param now 현재 시간
     */
    private void sendRoutineStartReminderWithWeather(Schedule schedule, User user, LocalDateTime now) {
        try {
            String title = "루틴 시작 알림";
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append(String.format("'%s' 루틴이 1시간 후 시작됩니다!", schedule.getTitle()));

            Map<String, String> data = new HashMap<>();
            data.put("scheduleId", schedule.getId().toString());
            data.put("routineId", schedule.getRoutineId().toString());
            data.put("type", NOTIFICATION_TYPE_ROUTINE_START_REMINDER);
            data.put("startTime", schedule.getStartTime().toString());

            // 날씨 정보 조회 및 추가
            if (schedule.getDestinationY() != null && schedule.getDestinationX() != null) {
                try {
                    weatherApiService.getCurrentWeather(schedule.getDestinationY(), schedule.getDestinationX())
                        .subscribe(
                            weatherResponse -> {
                                // 날씨 정보를 알림 메시지에 추가
                                String weatherInfo = String.format("\n🌤️ 날씨: %s, 온도: %.1f°C",
                                    weatherResponse.getWeather().get(0).getDescription(),
                                    weatherResponse.getMain().getTemp());
                                bodyBuilder.append(weatherInfo);

                                // FCM 데이터에 날씨 정보 추가
                                data.put("hasWeather", "true");
                                data.put("weatherDescription", weatherResponse.getWeather().get(0).getDescription());
                                data.put("temperature", String.valueOf(weatherResponse.getMain().getTemp()));
                                data.put("feelsLike", String.valueOf(weatherResponse.getMain().getFeels_like()));
                                data.put("humidity", String.valueOf(weatherResponse.getMain().getHumidity()));
                                data.put("weatherIcon", weatherResponse.getWeather().get(0).getIcon());
                                data.put("weatherType", weatherApiService.determineWeatherType(weatherResponse));

                                // 알림 전송
                                sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                                    schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

                                log.info("루틴 시작 1시간 전 알림 전송 완료 (날씨 포함) - User ID: {}, Schedule ID: {}, 온도: {}°C",
                                    user.getId(), schedule.getId(), weatherResponse.getMain().getTemp());
                            },
                            error -> {
                                log.warn("루틴 시작 알림 중 날씨 정보 조회 실패 - Schedule ID: {}, 에러: {}",
                                    schedule.getId(), error.getMessage());

                                // 날씨 정보 없이 알림 전송
                                data.put("hasWeather", "false");
                                sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                                    schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);

                                log.info("루틴 시작 1시간 전 알림 전송 완료 (날씨 정보 없음) - User ID: {}, Schedule ID: {}",
                                    user.getId(), schedule.getId());
                            }
                        );
                } catch (Exception e) {
                    log.error("날씨 API 호출 중 오류 발생 - Schedule ID: {}, 에러: {}", schedule.getId(), e.getMessage());

                    // 날씨 정보 없이 알림 전송
                    data.put("hasWeather", "false");
                    sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                        schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);
                }
            } else {
                // 좌표 정보가 없는 경우 날씨 정보 없이 알림 전송
                log.info("스케줄 ID {}에 좌표 정보가 없어 날씨 정보 없이 루틴 시작 알림을 전송합니다.", schedule.getId());
                data.put("hasWeather", "false");
                sendAndSaveNotification(user, title, bodyBuilder.toString(), data,
                    schedule.getId(), NOTIFICATION_TYPE_ROUTINE_START_REMINDER);
            }

        } catch (Exception e) {
            log.error("루틴 시작 1시간 전 알림 전송 실패 - User ID: {}, Schedule ID: {}, 에러: {}",
                user.getId(), schedule.getId(), e.getMessage(), e);
        }
    }
}
