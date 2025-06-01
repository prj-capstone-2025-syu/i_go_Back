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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
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

    // 루틴 기반 일정 생성 (종료 시간을 직접 받음)
    public Schedule createFromRoutine(Long userId, Long routineId, String title, LocalDateTime startTime,
                                      LocalDateTime endTime, String location, String memo, String supplies, String category) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 루틴 ID가 null인 경우 예외 처리 (필수 항목)
        if (routineId == null) {
            throw new IllegalArgumentException("루틴을 선택해야 합니다.");
        }

        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("해당 루틴이 존재하지 않습니다: " + routineId));

        if (!routine.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 루틴에 대한 권한이 없습니다.");
        }

        // 종료 시간 유효성 검사
        if (endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간이 유효하지 않습니다. 종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        Schedule schedule = Schedule.builder()
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
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
                                   LocalDateTime startTime, LocalDateTime endTime, String location,
                                   String memo, String supplies, String category) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("수정할 일정을 찾을 수 없습니다. ID: " + scheduleId));

        if (!schedule.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 수정 권한이 없습니다.");
        }

        // 루틴 ID가 제공된 경우 유효성 검사
        if (routineId != null) {
            Routine routine = routineRepository.findById(routineId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 루틴이 존재하지 않습니다: " + routineId));

            if (!routine.getUser().getId().equals(userId)) {
                throw new IllegalArgumentException("해당 루틴에 대한 권한이 없습니다.");
            }
        }

        // 종료 시간 유효성 검사
        if (endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간이 유효하지 않습니다. 종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        // 일정 정보 업데이트
        schedule.setTitle(title);
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setLocation(location);
        schedule.setMemo(memo);
        schedule.setSupplies(supplies);
        schedule.setCategory(Category.valueOf(category.toUpperCase()));
        schedule.setRoutineId(routineId);

        // Google Calendar 이벤트 업데이트
        try {
            if (schedule.getGoogleCalendarEventId() != null) {
                googleCalendarService.updateEvent(schedule, userId);
            } else {
                // Google Calendar Event ID가 없는 경우 새로 생성
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
                // 구글 캘린더 이벤트 삭제 실패 시에도 DB에서는 일정을 삭제하도록 예외를 다시 던지지 않음
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
                                  LocalDateTime endTime, String location, String memo, String category) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 종료 시간 유효성 검사
        if (endTime == null || endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("종료 시간이 유효하지 않습니다. 종료 시간은 시작 시간보다 뒤여야 합니다.");
        }

        Schedule schedule = Schedule.builder()
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .memo(memo)
                .category(Category.valueOf(category.toUpperCase()))
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
        // 가장 최근에 시작된 진행 중인 일정 1개를 가져온다.
        List<Schedule> schedules = scheduleRepository.findLatestInProgressSchedulesByUserId(userId, now, PageRequest.of(0, 1));
        return schedules.isEmpty() ? Optional.empty() : Optional.of(schedules.get(0));
    }

    //파이어베이스 -> 매 분마다 실행되어 스케줄 시작 알림 및 루틴 아이템 시작 알림을 전송
    @Scheduled(cron = "0 * * * * ?") // 매 분 0초에 실행
    public void sendScheduleAndRoutineNotifications() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkRangeStart = now; // 현재 시간부터
        LocalDateTime checkRangeEnd = now.plusMinutes(1).minusSeconds(1); // 다음 분 시작 전까지 (정확히 1분 간격)

        // 1. 스케줄 시작 알림 처리
        // 알림을 보낼 시간 범위 (예: 현재 시간부터 5분 후 사이에 시작하는 일정) -> 기존 로직 유지 또는 조정
        LocalDateTime scheduleNotificationRangeStart = now;
        LocalDateTime scheduleNotificationRangeEnd = now.plusMinutes(5);

        log.info("스케줄 및 루틴 알림 작업 실행: 현재 시간 {}", now);

        List<Schedule> upcomingSchedules = scheduleRepository.findByStartTimeBetweenAndStatusAndUserFcmTokenIsNotNull(
                scheduleNotificationRangeStart,
                scheduleNotificationRangeEnd,
                Schedule.ScheduleStatus.PENDING
        );

        for (Schedule schedule : upcomingSchedules) {
            User user = schedule.getUser();
            if (user != null && user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                // 스케줄 시작 알림이 이미 보내졌는지 확인 (relatedId: schedule.id, type: "SCHEDULE_START")
                Optional<com.example.demo.entity.fcm.Notification> existingNotification = notificationRepository
                        .findByUserAndRelatedIdAndNotificationType(user, schedule.getId(), "SCHEDULE_START");

                if (existingNotification.isEmpty()) {
                    String title = "일정 시작 알림";
                    String body = "'" + schedule.getTitle() + "' 일정이 곧 시작됩니다!";
                    Map<String, String> data = new HashMap<>();
                    data.put("scheduleId", schedule.getId().toString());
                    data.put("type", "SCHEDULE_START");

                    try {
                        fcmService.sendMessageToToken(user.getFcmToken(), title, body, data);
                        log.info("스케줄 시작 알림 전송 성공: 사용자 ID {}, 스케줄 ID {}", user.getId(), schedule.getId());

                        com.example.demo.entity.fcm.Notification notification = com.example.demo.entity.fcm.Notification.builder()
                                .user(user)
                                .title(title)
                                .body(body)
                                .relatedId(schedule.getId())
                                .notificationType("SCHEDULE_START")
                                .build(); // isRead, createdAt은 @PrePersist, @CreationTimestamp로 자동 설정
                        notificationRepository.save(notification);
                        log.info("스케줄 시작 알림 DB 저장 완료: 알림 ID {}", notification.getId());

                        // 스케줄 상태를 IN_PROGRESS로 변경 (알림을 보냈으므로)
                        schedule.setStatus(Schedule.ScheduleStatus.IN_PROGRESS);
                        scheduleRepository.save(schedule);
                        log.info("스케줄 ID {} 상태를 IN_PROGRESS로 변경", schedule.getId());

                    } catch (Exception e) {
                        log.error("스케줄 시작 알림 전송/저장 실패: 사용자 ID {}, 스케줄 ID {}. 오류: {}", user.getId(), schedule.getId(), e.getMessage(), e);
                    }
                } else {
                    log.info("스케줄 ID {} 시작 알림이 이미 전송되었습니다. 건너<0xEB><0x9B><0x84>니다.", schedule.getId());
                    // PENDING 상태인데 알림이 이미 있다면, IN_PROGRESS로 변경하는 로직 추가 고려
                    if (schedule.getStatus() == Schedule.ScheduleStatus.PENDING) {
                        schedule.setStatus(Schedule.ScheduleStatus.IN_PROGRESS);
                        scheduleRepository.save(schedule);
                        log.info("스케줄 ID {} 상태를 IN_PROGRESS로 변경 (기존 알림 발견)", schedule.getId());
                    }
                }
            }
        }

        // 2. 루틴 아이템 시작 알림 처리 (IN_PROGRESS 상태인 스케줄 대상)
        List<Schedule> inProgressSchedules = scheduleRepository.findByStatusAndUserFcmTokenIsNotNull(Schedule.ScheduleStatus.IN_PROGRESS);

        for (Schedule schedule : inProgressSchedules) {
            if (schedule.getRoutineId() == null) {
                // 루틴이 없는 스케줄은 여기서 더 처리할 필요 없음. (선택: 특정 시간 후 COMPLETED로 변경 로직 추가 가능)
                // 예를 들어, schedule.getEndTime() 이 과거면 COMPLETED로 변경
                if (schedule.getEndTime() != null && schedule.getEndTime().isBefore(now)) {
                    schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
                    scheduleRepository.save(schedule);
                    log.info("루틴 없는 스케줄 ID {} 종료시간 도달, COMPLETED로 변경", schedule.getId());
                }
                continue;
            }

            User user = schedule.getUser();
            if (user == null || user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                log.warn("스케줄 ID {} (루틴 ID {}) 에 대한 사용자 정보가 없거나 FCM 토큰이 없습니다. 루틴 알림을 건너<0xEB><0x9B><0x84>니다.", schedule.getId(), schedule.getRoutineId());
                continue;
            }

            List<CalculatedRoutineItemTime> calculatedItems = routineService.calculateRoutineItemTimes(schedule.getRoutineId(), schedule.getStartTime());
            boolean allItemsCompletedForThisSchedule = true; // 해당 스케줄의 모든 루틴 아이템이 완료되었는지 여부

            for (CalculatedRoutineItemTime itemTime : calculatedItems) {
                // 현재 시간(checkRangeStart ~ checkRangeEnd)에 시작해야 하는 루틴 아이템인지 확인
                // itemTime.getStartTime()이 now 와 거의 일치하는지 확인 (매 분 실행되므로)
                // 예를 들어, itemTime.getStartTime()이 09:00:00 이고, now가 09:00:00 ~ 09:00:59 사이면 알림
                if (itemTime.getStartTime().isEqual(checkRangeStart) || (itemTime.getStartTime().isAfter(checkRangeStart) && itemTime.getStartTime().isBefore(checkRangeEnd.plusSeconds(1)))) {

                    Optional<com.example.demo.entity.fcm.Notification> existingNotification = notificationRepository
                            .findByUserAndRelatedIdAndNotificationType(user, itemTime.getRoutineItemId(), "ROUTINE_ITEM_START");

                    if (existingNotification.isEmpty()) {
                        String title = "'" + schedule.getTitle() + "' 진행 중";
                        String body = "루틴: '" + itemTime.getRoutineItemName() + "' 시작 시간입니다. (예상 소요: " + itemTime.getDurationMinutes() + "분)";
                        Map<String, String> data = new HashMap<>();
                        data.put("scheduleId", schedule.getId().toString());
                        data.put("routineId", itemTime.getRoutineId().toString());
                        data.put("routineItemId", itemTime.getRoutineItemId().toString());
                        data.put("type", "ROUTINE_ITEM_START");

                        try {
                            fcmService.sendMessageToToken(user.getFcmToken(), title, body, data);
                            log.info("루틴 아이템 시작 알림 전송 성공: 사용자 ID {}, 스케줄 ID {}, 루틴 아이템 ID {}", user.getId(), schedule.getId(), itemTime.getRoutineItemId());

                            com.example.demo.entity.fcm.Notification notification = com.example.demo.entity.fcm.Notification.builder()
                                    .user(user)
                                    .title(title)
                                    .body(body)
                                    .relatedId(itemTime.getRoutineItemId()) // 루틴 아이템 ID 저장
                                    .notificationType("ROUTINE_ITEM_START")
                                    .build();
                            notificationRepository.save(notification);
                            log.info("루틴 아이템 시작 알림 DB 저장 완료: 알림 ID {}", notification.getId());

                        } catch (Exception e) {
                            log.error("루틴 아이템 시작 알림 전송/저장 실패: 사용자 ID {}, 루틴 아이템 ID {}. 오류: {}", user.getId(), itemTime.getRoutineItemId(), e.getMessage(), e);
                        }
                    } else {
                        log.info("루틴 아이템 ID {} 시작 알림이 이미 전송되었습니다. 건너<0xEB><0x9B><0x84>니다.", itemTime.getRoutineItemId());
                    }
                }

                // 이 루틴 아이템이 아직 완료되지 않았는지 (즉, 현재 시간 < 아이템 종료 시간) 확인
                if (itemTime.getEndTime().isAfter(now)) {
                    allItemsCompletedForThisSchedule = false;
                }
            }

            // 모든 루틴 아이템의 예상 종료 시간이 현재 시간보다 이전이면 스케줄을 COMPLETED로 변경
            if (allItemsCompletedForThisSchedule && !calculatedItems.isEmpty()) {
                // 마지막 아이템의 종료 시간 확인
                CalculatedRoutineItemTime lastItem = calculatedItems.get(calculatedItems.size() - 1);
                if (lastItem.getEndTime().isBefore(now) || lastItem.getEndTime().isEqual(now)) {
                    schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
                    scheduleRepository.save(schedule);
                    log.info("스케줄 ID {}의 모든 루틴 아이템 완료, COMPLETED로 변경", schedule.getId());
                }
            } else if (calculatedItems.isEmpty() && schedule.getEndTime() != null && schedule.getEndTime().isBefore(now)) {
                // 루틴 아이템이 없지만 스케줄 종료 시간이 지난 경우
                schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
                scheduleRepository.save(schedule);
                log.info("루틴 아이템 없는 스케줄 ID {} 종료시간 도달, COMPLETED로 변경", schedule.getId());
            }
        }
    }
}