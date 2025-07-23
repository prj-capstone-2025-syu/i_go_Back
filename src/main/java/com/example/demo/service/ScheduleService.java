package com.example.demo.service;

import com.example.demo.dto.routine.CalculatedRoutineItemTime;
import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.schedule.Category;
import com.example.demo.entity.user.User;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.ScheduleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {


    private final ScheduleRepository scheduleRepository;
    private final RoutineRepository routineRepository;
    private final GoogleCalendarService googleCalendarService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final FCMService fcmService;
    private final RoutineService routineService;

    private static final String NOTIFICATION_TYPE_SCHEDULE_START = "SCHEDULE_START";
    private static final String NOTIFICATION_TYPE_ROUTINE_ITEM_START = "ROUTINE_ITEM_START";
    private static final String NOTIFICATION_TYPE_SUPPLIES_REMINDER = "SUPPLIES_REMINDER";
    private static final int SUPPLIES_NOTIFICATION_MINUTES_BEFORE = 5; // 준비물 알림: 일정 시작 X분 전


    // 루틴 기반 일정 생성 (종료 시간을 직접 받음)
    public Schedule createFromRoutine(Long userId, Long routineId, String title, LocalDateTime startTime,
                                      LocalDateTime endTime, String startLocation, Double startX, Double startY,
                                      String location, Double destinationX, Double destinationY,
                                      String memo, String supplies, String category) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (routineId == null) {
            throw new IllegalArgumentException("루틴을 선택해야 합니다.");
        }

        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("해당 루틴이 존재하지 않습니다: " + routineId));

        if (!routine.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 루틴에 대한 권한이 없습니다.");
        }

        if (endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간이 유효하지 않습니다. 종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        Schedule schedule = Schedule.builder()
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .startLocation(startLocation)
                .startX(startX)
                .startY(startY)
                .location(location)
                .destinationX(destinationX)
                .destinationY(destinationY)
                .memo(memo)
                .category(Category.valueOf(category.toUpperCase()))
                .routineId(routineId)
                .supplies(supplies)
                .user(user)
                .status(Schedule.ScheduleStatus.PENDING)
                .build();

        try {
            String eventId = googleCalendarService.createEvent(schedule, userId);
            schedule.setGoogleCalendarEventId(eventId);
        } catch (Exception e) {
            log.error("Google Calendar 이벤트 생성 실패 (User ID: {}): {}. 일정은 DB에 저장됩니다.", userId, e.getMessage(), e);
            schedule.setGoogleCalendarEventId(null);
        }

        return scheduleRepository.save(schedule);
    }

    // 날짜별 일정 조회
    @Transactional(readOnly = true)
    public List<Schedule> getSchedulesByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return scheduleRepository.findByUserIdAndStartTimeBetween(userId, start, end);
    }

    // 일정 수정
    public Schedule updateSchedule(Long userId, Long scheduleId, Long routineId, String title,
                                   LocalDateTime startTime, LocalDateTime endTime,
                                   String startLocation, Double startX, Double startY,
                                   String location, Double destinationX, Double destinationY,
                                   String memo, String supplies, String category) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("수정할 일정을 찾을 수 없습니다. ID: " + scheduleId));

        if (!schedule.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 수정 권한이 없습니다.");
        }

        if (routineId != null) {
            Routine routine = routineRepository.findById(routineId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 루틴이 존재하지 않습니다: " + routineId));

            if (!routine.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("해당 루틴에 대한 권한이 없습니다.");
            }
        }

        if (endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간이 유효하지 않습니다. 종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        schedule.setTitle(title);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setStartLocation(startLocation);
        schedule.setStartX(startX);
        schedule.setStartY(startY);
        schedule.setLocation(location);
        schedule.setDestinationX(destinationX);
        schedule.setDestinationY(destinationY);
        schedule.setMemo(memo);
        schedule.setSupplies(supplies);
        schedule.setCategory(Category.valueOf(category.toUpperCase()));
        schedule.setRoutineId(routineId);

        try {
            if (schedule.getGoogleCalendarEventId() != null) {
                googleCalendarService.updateEvent(schedule, userId);
            } else {
                String eventId = googleCalendarService.createEvent(schedule, userId);
                schedule.setGoogleCalendarEventId(eventId);
            }
        } catch (Exception e) {
            log.error("Google Calendar 이벤트 업데이트 실패 (User ID: {}, Schedule ID: {}): {}. 일정은 DB에 저장됩니다.",
                    userId, scheduleId, e.getMessage(), e);
        }

        return scheduleRepository.save(schedule);
    }

    // 특정 일정 조회
    @Transactional(readOnly = true)
    public Schedule getScheduleById(Long userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. ID: " + scheduleId));

        if (!schedule.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 조회 권한이 없습니다.");
        }

        return schedule;
    }

    // 일정 삭제
    public void deleteSchedule(Long userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 일정을 찾을 수 없습니다. ID: " + scheduleId));

        if (!schedule.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 삭제 권한이 없습니다.");
        }

        if (schedule.getGoogleCalendarEventId() != null && !schedule.getGoogleCalendarEventId().isEmpty()) {
            try {
                googleCalendarService.deleteEvent(schedule.getGoogleCalendarEventId(), userId);
            } catch (Exception e) {
                log.error("Google Calendar 이벤트 삭제 실패 (User ID: {}, Event ID: {}): {}. DB에서는 일정이 삭제됩니다.", userId, schedule.getGoogleCalendarEventId(), e.getMessage(), e);
            }
        }
        scheduleRepository.delete(schedule);
    }


    //다가오는 일정 조회 (최대 3개)
    @Transactional(readOnly = true)
    public List<Schedule> getUpcomingSchedules(Long userId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return scheduleRepository.findByUserIdAndStartTimeAfterOrderByStartTimeAsc(userId, now, PageRequest.of(0, limit));
    }

    public Schedule createSchedule(Long userId, String title, LocalDateTime startTime,
                                   LocalDateTime endTime, String location, String memo, String category, String supplies) { // supplies 파라미터 추가
        // Log the received memo value
        log.info("ScheduleService.createSchedule called with memo: '{}'", memo);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간이 유효하지 않습니다. 종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        Schedule schedule = Schedule.builder()
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .memo(memo) // memo 설정
                .category(Category.valueOf(category.toUpperCase()))
                .user(user)
                .supplies(supplies) // supplies 설정
                .status(Schedule.ScheduleStatus.PENDING)
                .build();

        try {
            String eventId = googleCalendarService.createEvent(schedule, userId);
            schedule.setGoogleCalendarEventId(eventId);
        } catch (Exception e) {
            log.error("Google Calendar 이벤트 생성 실패 (User ID: {}): {}. 일정은 DB에 저장됩니다.", userId, e.getMessage(), e);
            schedule.setGoogleCalendarEventId(null);
        }

        return scheduleRepository.save(schedule);
    }

    public List<Schedule> findSchedulesByTitleAndTime(Long userId, String title, LocalDateTime dateTime) {
        return scheduleRepository.findByUserIdAndTitleAndStartTime(userId, title, dateTime);
    }

    public List<Schedule> findSchedulesByTitle(Long userId, String title) {
        return scheduleRepository.findByUserIdAndTitle(userId, title);
    }

    public List<Schedule> findSchedulesByTime(Long userId, LocalDateTime dateTime) {
        return scheduleRepository.findByUserIdAndStartTime(userId, dateTime);
    }

    @Transactional(readOnly = true)
    public Optional<Schedule> getLatestInProgressSchedule(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Schedule> schedules = scheduleRepository.findLatestInProgressSchedulesByUserId(userId, now, PageRequest.of(0, 1));
        return schedules.isEmpty() ? Optional.empty() : Optional.of(schedules.get(0));
    }

    @Scheduled(cron = "0 * * * * ?") // 매 분 0초에 실행
    public void sendScheduleAndRoutineNotifications() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0); // 현재 시간 (초, 나노초 제거)
        log.info("스케줄 및 루틴 알림 작업 실행: 현재 시간 {}", now);

        // 1. 스케줄 시작 알림 및 준비물 알림 처리 (PENDING 상태인 스케줄 대상)
        //    알림 범위: 현재 시간 ~ 5분 후 시작하는 일정 (스케줄 시작 알림)
        //    준비물 알림: 설정된 시간(예: 30분) 전에 발송
        LocalDateTime scheduleNotificationRangeStart = now;
        LocalDateTime scheduleNotificationRangeEnd = now.plusMinutes(5); // 다음 5분 이내 시작될 일정

        List<Schedule> pendingSchedules = scheduleRepository.findByStartTimeBetweenAndStatusAndUserFcmTokenIsNotNull(
                scheduleNotificationRangeStart, // 지금부터
                scheduleNotificationRangeEnd.plusMinutes(SUPPLIES_NOTIFICATION_MINUTES_BEFORE), // 준비물 알림 고려하여 더 넓은 범위 조회
                Schedule.ScheduleStatus.PENDING
        );

        for (Schedule schedule : pendingSchedules) {
            User user = schedule.getUser();
            if (user == null || user.getFcmToken() == null || user.getFcmToken().isEmpty() || !user.isNotificationsEnabled()) {
                continue; // 기본 조건 미충족 시 건너뛰기
            }

            // 1-1. 준비물 알림 (설정된 시간 전)
            // schedule.getStartTime()이 (now + SUPPLIES_NOTIFICATION_MINUTES_BEFORE) 와 일치하는지 확인
            LocalDateTime suppliesNotificationTime = schedule.getStartTime().minusMinutes(SUPPLIES_NOTIFICATION_MINUTES_BEFORE);
            if (user.isNotifySupplies() && schedule.getSupplies() != null && !schedule.getSupplies().isEmpty() &&
                    suppliesNotificationTime.isEqual(now)) {

                Optional<com.example.demo.entity.fcm.Notification> existingSuppliesNotification = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);

                if (existingSuppliesNotification.isEmpty()) {
                    String title = "🎒 준비물 체크";
                    String body = String.format("'%s'이(가) %d분 후 시작돼요!\n📋 준비물: %s",
                        schedule.getTitle(), SUPPLIES_NOTIFICATION_MINUTES_BEFORE, schedule.getSupplies());
                    Map<String, String> data = new HashMap<>();
                    data.put("scheduleId", schedule.getId().toString());
                    data.put("type", NOTIFICATION_TYPE_SUPPLIES_REMINDER);
                    sendAndSaveNotification(user, title, body, data, schedule.getId(), NOTIFICATION_TYPE_SUPPLIES_REMINDER);
                }
            }

            // 1-2. 스케줄 시작 알림 (일정 시작 시간 정각)
            // schedule.getStartTime()이 now 와 일치하는지 확인
            if (user.isNotifyNextSchedule() && schedule.getStartTime().isEqual(now)) {
                Optional<com.example.demo.entity.fcm.Notification> existingScheduleStartNotification = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), NOTIFICATION_TYPE_SCHEDULE_START);

                if (existingScheduleStartNotification.isEmpty()) {
                    String title = "🚀 일정 시작";
                    StringBuilder bodyBuilder = new StringBuilder();
                    bodyBuilder.append(String.format("'%s'이(가) 지금 시작돼요!", schedule.getTitle()));

                    // 도착지 정보 추가
                    if (schedule.getLocation() != null && !schedule.getLocation().trim().isEmpty()) {
                        bodyBuilder.append(String.format("\n📍 도착지: %s", schedule.getLocation()));
                    }

                    // 시작 위치 정보도 있다면 추가
                    if (schedule.getStartLocation() != null && !schedule.getStartLocation().trim().isEmpty()) {
                        bodyBuilder.append(String.format("\n🏠 출발지: %s", schedule.getStartLocation()));
                    }

                    String body = bodyBuilder.toString();
                    Map<String, String> data = new HashMap<>();
                    data.put("scheduleId", schedule.getId().toString());
                    data.put("type", NOTIFICATION_TYPE_SCHEDULE_START);

                    sendAndSaveNotification(user, title, body, data, schedule.getId(), NOTIFICATION_TYPE_SCHEDULE_START);
                    schedule.setStatus(Schedule.ScheduleStatus.IN_PROGRESS);
                    scheduleRepository.save(schedule);
                    log.info("스케줄 ID {} 상태를 IN_PROGRESS로 변경 (시작 알림 발송)", schedule.getId());
                } else {
                    // 이미 알림이 갔지만 PENDING 상태라면 IN_PROGRESS로 변경
                    if (schedule.getStatus() == Schedule.ScheduleStatus.PENDING) {
                        schedule.setStatus(Schedule.ScheduleStatus.IN_PROGRESS);
                        scheduleRepository.save(schedule);
                        log.info("스케줄 ID {} 상태를 IN_PROGRESS로 변경 (기존 시작 알림 발견)", schedule.getId());
                    }
                }
            }
        }

        // 2. 루틴 아이템 시작 알림 처리 (IN_PROGRESS 상태인 스케줄 대상)
        List<Schedule> inProgressSchedules = scheduleRepository.findByStatusAndUserFcmTokenIsNotNull(Schedule.ScheduleStatus.IN_PROGRESS);

        for (Schedule schedule : inProgressSchedules) {
            User user = schedule.getUser();
            if (user == null || user.getFcmToken() == null || user.getFcmToken().isEmpty() ||
                    !user.isNotificationsEnabled() || !user.isNotifyRoutineProgress() || schedule.getRoutineId() == null) {
                // 루틴 진행 알림 조건 미충족 또는 루틴 없는 경우, 또는 루틴 아이템 알림 설정 꺼진 경우
                // 루틴 없는 스케줄의 완료 처리
                if (schedule.getRoutineId() == null && schedule.getEndTime() != null && (schedule.getEndTime().isBefore(now) || schedule.getEndTime().isEqual(now))) {
                    schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
                    scheduleRepository.save(schedule);
                    log.info("루틴 없는 스케줄 ID {} 종료시간 도달, COMPLETED로 변경", schedule.getId());
                }
                continue;
            }

            List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(schedule.getRoutineId(), schedule.getStartTime());
            boolean allItemsCompletedForThisSchedule = true;

            for (CalculatedRoutineItemTime itemTime : calculatedItems) {
                // 루틴 아이템 시작 시간이 현재 시간(now)과 정확히 일치하는 경우
                if (itemTime.getStartTime().isEqual(now)) {
                    Optional<com.example.demo.entity.fcm.Notification> existingRoutineItemNotification = notificationRepository
                            .findByUserAndRelatedIdAndNotificationType(user, itemTime.getRoutineItemId(), NOTIFICATION_TYPE_ROUTINE_ITEM_START);

                    if (existingRoutineItemNotification.isEmpty()) {
                        String title = String.format("📋 %s", schedule.getTitle());
                        String body = String.format("🎯 %s 할 시간이에요!", itemTime.getRoutineItemName());
                        Map<String, String> data = new HashMap<>();
                        data.put("scheduleId", schedule.getId().toString());
                        data.put("routineId", itemTime.getRoutineId().toString());
                        data.put("routineItemId", itemTime.getRoutineItemId().toString());
                        data.put("type", NOTIFICATION_TYPE_ROUTINE_ITEM_START);
                        sendAndSaveNotification(user, title, body, data, itemTime.getRoutineItemId(), NOTIFICATION_TYPE_ROUTINE_ITEM_START);
                    } else {
                        log.info("루틴 아이템 ID {} 시작 알림이 이미 전송되었습니다. 건너뜁니다.", itemTime.getRoutineItemId());
                    }
                }

                if (itemTime.getEndTime().isAfter(now)) {
                    allItemsCompletedForThisSchedule = false;
                }
            }

            if (allItemsCompletedForThisSchedule && !calculatedItems.isEmpty()) {
                CalculatedRoutineItemTime lastItem = calculatedItems.get(calculatedItems.size() - 1);
                if (lastItem.getEndTime().isBefore(now) || lastItem.getEndTime().isEqual(now)) {
                    schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
                    scheduleRepository.save(schedule);
                    log.info("스케줄 ID {}의 모든 루틴 아이템 완료, COMPLETED로 변경", schedule.getId());
                }
            } else if (calculatedItems.isEmpty() && schedule.getEndTime() != null && (schedule.getEndTime().isBefore(now) || schedule.getEndTime().isEqual(now))) {
                schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
                scheduleRepository.save(schedule);
                log.info("루틴 아이템 없는 스케줄 ID {} 종료시간 도달, COMPLETED로 변경", schedule.getId());
            }
        }
    }

    private void sendAndSaveNotification(User user, String title, String body, Map<String, String> data, Long relatedId, String notificationType) {
        try {
            // FCM 토큰 유효성 재확인 (sendScheduleAndRoutineNotifications 에서 이미 확인했지만, 안전을 위해)
            if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                    log.warn("{} 알림 전송 시도: 사용자 ID {}의 FCM 토큰이 없습니다.", notificationType, user.getId());
                return;
            }

            fcmService.sendMessageToToken(user.getFcmToken(), title, body, data);
            log.info("{} 알림 전송 성공: 사용자 ID {}, 관련 ID {}", notificationType, user.getId(), relatedId);

            com.example.demo.entity.fcm.Notification notification = com.example.demo.entity.fcm.Notification.builder()
                    .user(user)
                    .title(title)
                    .body(body)
                    .relatedId(relatedId)
                    .notificationType(notificationType)
                    .build();
            notificationRepository.save(notification);
            log.info("{} 알림 DB 저장 완료: 알림 ID {}", notificationType, notification.getId());

        } catch (Exception e) {
            log.error("{} 알림 전송/저장 실패: 사용자 ID {}, 관련 ID {}. 오류: {}", notificationType, user.getId(), relatedId, e.getMessage(), e);
        }
    }

    public List<Schedule> findSchedulesByArgs(Long userId, Map<String, Object> args) {
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        // 1. 제목+시간 모두 있을 때
        if (title != null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            return findSchedulesByTitleAndTime(userId, title, dateTime);
        }

        // 2. 제목만 있을 때
        if (title != null && datetime == null) {
            return findSchedulesByTitle(userId, title);
        }

        // 3. 시간만 있을 때
        if (title == null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            // 해당 날짜의 00:00:00 부터 23:59:59.999999999 까지 조회하도록 수정
            LocalDateTime startTime = dateTime.toLocalDate().atStartOfDay();
            LocalDateTime endTime = dateTime.toLocalDate().atTime(23, 59, 59, 999999999);
            return getSchedulesByDateRange(userId, startTime, endTime);
        }

        // 4. 아무 정보도 없을 때 (오늘 일정 조회)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.withHour(0).withMinute(0);
        LocalDateTime endTime = now.withHour(23).withMinute(59);
        return getSchedulesByDateRange(userId, startTime, endTime);
    }

    public Schedule createScheduleByArgs(Long userId, Map<String, Object> args) {
        log.info("ScheduleService.createScheduleByArgs received args: {}", args); // Log the received arguments map
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        String location = (String) args.get("location");

        String memo = "";
        Object memoVal = args.get("memo");
        if (memoVal instanceof String && !((String) memoVal).isEmpty()) {
            memo = (String) memoVal;
        } else {
            Object notesVal = args.get("notes");
            if (notesVal instanceof String && !((String) notesVal).isEmpty()) {
                memo = (String) notesVal;
                log.info("Used 'notes' field for memo. Memo value: '{}'", memo);
            } else {
                Object descriptionVal = args.get("description");
                if (descriptionVal instanceof String && !((String) descriptionVal).isEmpty()) {
                    memo = (String) descriptionVal;
                    log.info("Used 'description' field for memo. Memo value: '{}'", memo);
                }
            }
        }

        String category = (String) args.getOrDefault("category", "PERSONAL");

        String supplies = "";
        Object suppliesVal = args.get("supplies");
        if (suppliesVal instanceof String && !((String) suppliesVal).isEmpty()) {
            supplies = (String) suppliesVal;
        } else if (suppliesVal instanceof List) {
            try {
                List<?> list = (List<?>) suppliesVal;
                supplies = list.stream().map(Object::toString).collect(Collectors.joining(", "));
                if (!supplies.isEmpty()) log.info("Used 'supplies' (List) field. Supplies value: '{}'", supplies);
            } catch (Exception e) { log.warn("Error processing 'supplies' as List: {}", e.getMessage()); }
        }

        if (supplies.isEmpty()) {
            Object itemsVal = args.get("items");
            if (itemsVal instanceof String && !((String) itemsVal).isEmpty()) {
                supplies = (String) itemsVal;
                if (!supplies.isEmpty()) log.info("Used 'items' (String) field for supplies. Supplies value: '{}'", supplies);
            } else if (itemsVal instanceof List) {
                try {
                    List<?> list = (List<?>) itemsVal;
                    supplies = list.stream().map(Object::toString).collect(Collectors.joining(", "));
                    if (!supplies.isEmpty()) log.info("Used 'items' (List) field for supplies. Supplies value: '{}'", supplies);
                } catch (Exception e) { log.warn("Error processing 'items' as List: {}", e.getMessage()); }
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime startTime = LocalDateTime.parse(datetime, formatter);
        LocalDateTime endTime = startTime.plusHours(1); // 기본 1시간

        return createSchedule(userId, title, startTime, endTime, location, memo, category, supplies); // supplies 전달
    }

    public boolean deleteScheduleByArgs(Long userId, Map<String, Object> args) {
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        // 1. 제목+시간 모두 있을 때
        if (title != null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            List<Schedule> candidates = findSchedulesByTitleAndTime(userId, title, dateTime);
            if (candidates.size() == 1) {
                deleteSchedule(userId, candidates.get(0).getId());
                return true;
            }
        }

        // 2. 제목만 있을 때
        if (title != null && datetime == null) {
            List<Schedule> candidates = findSchedulesByTitle(userId, title);
            if (candidates.size() == 1) {
                deleteSchedule(userId, candidates.get(0).getId());
                return true;
            }
        }

        // 3. 시간만 있을 때
        if (title == null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            List<Schedule> candidates = findSchedulesByTime(userId, dateTime);
            if (candidates.size() == 1) {
                deleteSchedule(userId, candidates.get(0).getId());
                return true;
            }
        }

        return false;
    }

    public Schedule updateScheduleByArgs(Long userId, Map<String, Object> args) {
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        String location = (String) args.get("location");
        String memo = (String) args.get("memo");
        String category = (String) args.getOrDefault("category", "PERSONAL");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime startTime = LocalDateTime.parse(datetime, formatter);
        LocalDateTime endTime = startTime.plusHours(1); // 기본 1시간

        // 기존 일정 찾기
        List<Schedule> candidates = findSchedulesByTitleAndTime(userId, title, startTime);
        if (candidates.size() != 1) {
            throw new IllegalArgumentException("수정할 일정을 찾을 수 없습니다.");
        }

        Schedule schedule = candidates.get(0);
        return updateSchedule(
            userId,
            schedule.getId(),
            null, // routineId는 null로 설정
            title,
            startTime,
            endTime,
            "", // startLocation
            0.0, // startX
            0.0, // startY
            location,
            0.0, // destinationX
            0.0, // destinationY
            memo,
            null, // supplies는 null로 설정
            category
        );
    }
}

