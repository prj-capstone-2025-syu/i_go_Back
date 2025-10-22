package com.example.demo.service;

import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.schedule.Category;
import com.example.demo.entity.user.User;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.ScheduleRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final RoutineService routineService;
    private final ScheduleNotificationService scheduleNotificationService;
    private final TransportService transportService;

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

        // 교통 시간 계산 (좌표가 있는 경우에만)
        Integer originalDrivingTime = null;
        Integer originalTransitTime = null;

        if (startX != null && startY != null && destinationX != null && destinationY != null) {
            try {
                com.example.demo.dto.transport.TransportTimeRequest transportRequest =
                        new com.example.demo.dto.transport.TransportTimeRequest();
                transportRequest.setStartX(startX);
                transportRequest.setStartY(startY);
                transportRequest.setEndX(destinationX);
                transportRequest.setEndY(destinationY);
                transportRequest.setRemoteEvent(false);

                com.example.demo.dto.transport.TransportTimeResponse transportTimes =
                        transportService.calculateAllTransportTimes(transportRequest);

                originalDrivingTime = transportTimes.getDriving();
                originalTransitTime = transportTimes.getTransit();

                log.info("📊 [ScheduleService] 원본 교통 시간 저장 - Schedule: '{}', 자차: {}분, 대중교통: {}분",
                        title, originalDrivingTime, originalTransitTime);
            } catch (Exception e) {
                log.error("❌ [ScheduleService] 교통 시간 계산 실패 - Schedule: '{}', 에러: {}",
                        title, e.getMessage());
            }
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
                .originalDrivingTime(originalDrivingTime)
                .originalTransitTime(originalTransitTime)
                .build();

        try {
            String eventId = googleCalendarService.createEvent(schedule, userId);
            schedule.setGoogleCalendarEventId(eventId);
        } catch (Exception e) {
            log.error("Google Calendar 이벤트 생성 실패 (User ID: {}): {}. 일정은 DB에 저장됩니다.", userId, e.getMessage(), e);
            schedule.setGoogleCalendarEventId(null);
        }

        Schedule savedSchedule = scheduleRepository.save(schedule);

        // 지연 등록 알림 처리 - 루틴 시작 시간 기준으로 체크
        LocalDateTime now = LocalDateTime.now();
        if (routineId != null) {
            try {
                // 루틴 시작 시간 계산 (루틴의 첫 번째 아이템 시작 시간)
                LocalDateTime routineStartTime = routineService.calculateRoutineStartTime(routineId, startTime);

                if (routineStartTime != null && routineStartTime.isBefore(now)) {
                    log.info("🚨 [ScheduleService] 지연 등록 감지 - Schedule ID: {}, 루틴 시작시간: {}, 스케줄 시작시간: {}, 현재시간: {}",
                            savedSchedule.getId(), routineStartTime, startTime, now);

                    String currentRoutineItemName = routineService.getCurrentRoutineItemName(routineId, startTime, now);
                    if (currentRoutineItemName != null) {
                        log.info("📱 [ScheduleService] 지연 등록 알림 전송 시작 - Schedule ID: {}, Current Item: '{}', User ID: {}",
                                savedSchedule.getId(), currentRoutineItemName, user.getId());

                        scheduleNotificationService.sendDelayedRoutineItemNotification(savedSchedule, user, currentRoutineItemName);

                        log.info("✅ [ScheduleService] 지연 등록 알림 처리 완료 - Schedule ID: {}, Current Item: '{}'",
                                savedSchedule.getId(), currentRoutineItemName);
                    } else {
                        log.info("⚠️ [ScheduleService] 지연 등록이지만 현재 시간에 해당하는 루틴 아이템 없음 - Schedule ID: {}",
                                savedSchedule.getId());
                    }
                } else {
                    log.info("⏰ [ScheduleService] 정상 등록 - Schedule ID: {}, 루틴 시작까지 남은 시간: {}분",
                            savedSchedule.getId(),
                            routineStartTime != null ? java.time.Duration.between(now, routineStartTime).toMinutes() : "N/A");
                }
            } catch (Exception e) {
                log.error("❌ [ScheduleService] 지연 등록 알림 처리 중 오류 - Schedule ID: {}, 오류: {}",
                        savedSchedule.getId(), e.getMessage(), e);
            }
        }

        return savedSchedule;
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

        // 기존 루틴 ID 저장
        Long previousRoutineId = schedule.getRoutineId();

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

        // 좌표 변경 여부 확인
        boolean coordinatesChanged = false;
        if ((startX != null && !startX.equals(schedule.getStartX())) ||
            (startY != null && !startY.equals(schedule.getStartY())) ||
            (destinationX != null && !destinationX.equals(schedule.getDestinationX())) ||
            (destinationY != null && !destinationY.equals(schedule.getDestinationY()))) {
            coordinatesChanged = true;
        }

        // 좌표가 변경되었으면 교통 시간 재계산
        if (coordinatesChanged && startX != null && startY != null && destinationX != null && destinationY != null) {
            try {
                com.example.demo.dto.transport.TransportTimeRequest transportRequest =
                        new com.example.demo.dto.transport.TransportTimeRequest();
                transportRequest.setStartX(startX);
                transportRequest.setStartY(startY);
                transportRequest.setEndX(destinationX);
                transportRequest.setEndY(destinationY);
                transportRequest.setRemoteEvent(false);

                com.example.demo.dto.transport.TransportTimeResponse transportTimes =
                        transportService.calculateAllTransportTimes(transportRequest);

                schedule.setOriginalDrivingTime(transportTimes.getDriving());
                schedule.setOriginalTransitTime(transportTimes.getTransit());

                log.info("📊 [ScheduleService] 일정 수정 - 교통 시간 재계산 완료 - Schedule ID: {}, 자차: {}분, 대중교통: {}분",
                        scheduleId, transportTimes.getDriving(), transportTimes.getTransit());
            } catch (Exception e) {
                log.error("❌ [ScheduleService] 일정 수정 - 교통 시간 재계산 실패 - Schedule ID: {}, 에러: {}",
                        scheduleId, e.getMessage());
            }
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

        Schedule savedSchedule = scheduleRepository.save(schedule);

        // 지연 등록 알림 처리 (루틴이 새로 추가되었거나 변경된 경우) - 루틴 시작 시간 기준으로 체크
        LocalDateTime now = LocalDateTime.now();
        if (routineId != null && !routineId.equals(previousRoutineId)) {
            try {
                // 루틴 시작 시간 계산 (루틴의 첫 번째 아이템 시작 시간)
                LocalDateTime routineStartTime = routineService.calculateRoutineStartTime(routineId, startTime);

                if (routineStartTime != null && routineStartTime.isBefore(now)) {
                    String currentRoutineItemName = routineService.getCurrentRoutineItemName(routineId, startTime, now);
                    if (currentRoutineItemName != null) {
                        scheduleNotificationService.sendDelayedRoutineItemNotification(savedSchedule, schedule.getUser(), currentRoutineItemName);
                        log.info("✅ 일정 수정 시 지연 등록 알림 처리 완료 - Schedule ID: {}, Current Item: '{}', 루틴 시작시간: {}",
                                savedSchedule.getId(), currentRoutineItemName, routineStartTime);
                    }
                }
            } catch (Exception e) {
                log.error("❌ 일정 수정 시 지연 등록 알림 처리 중 오류 - Schedule ID: {}, 오류: {}",
                        savedSchedule.getId(), e.getMessage(), e);
            }
        }

        return savedSchedule;
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
                                   LocalDateTime endTime, String startLocation, String location, String memo,
                                   String category, String supplies, Double startX, Double startY,
                                   Double destinationX, Double destinationY) {
        log.info("ScheduleService.createSchedule called with startLocation: '{}', location: '{}', memo: '{}'",
                startLocation, location, memo);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

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
                .user(user)
                .supplies(supplies)
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

        // 기존 쿼리로 후보 일정들을 더 많이 가져옴 (루틴이 있는 경우를 대비)
        List<Schedule> schedules = scheduleRepository.findLatestInProgressSchedulesByUserId(userId, now, PageRequest.of(0, 10));

        if (schedules.isEmpty()) {
            return Optional.empty();
        }

        // 루틴 시작 시간을 고려하여 실제 진행 중인 일정 필터링
        for (Schedule schedule : schedules) {
            LocalDateTime actualStartTime = schedule.getStartTime();
            LocalDateTime displayStartTime = schedule.getStartTime(); // 표시 시작 시간 (루틴 시작 1시간 전)

            // 루틴이 있는 경우 루틴 시작 시간 계산
            if (schedule.getRoutineId() != null) {
                try {
                    LocalDateTime routineStartTime = routineService.calculateRoutineStartTime(
                            schedule.getRoutineId(), schedule.getStartTime());

                    if (routineStartTime != null) {
                        actualStartTime = routineStartTime;
                        // 루틴 시작 1시간 전부터 표시
                        displayStartTime = routineStartTime.minusHours(1);
                        log.debug("[ScheduleService] 루틴이 있는 스케줄 - Schedule ID: {}, 루틴 시작: {}, 표시 시작(1시간 전): {}, 스케줄 시작: {}, 현재: {}",
                                schedule.getId(), routineStartTime, displayStartTime, schedule.getStartTime(), now);
                    }
                } catch (Exception e) {
                    log.error("[ScheduleService] 루틴 시작 시간 계산 실패 - Schedule ID: {}, 오류: {}",
                            schedule.getId(), e.getMessage());
                    // 오류 발생 시 스케줄 시작 시간 사용
                }
            } else {
                // 루틴이 없는 경우에도 스케줄 시작 시간 사용
                displayStartTime = schedule.getStartTime();
            }

            // 실제 진행 중인지 확인: (루틴 시작 1시간 전) <= 현재 시간 < 스케줄 종료 시간
            if (!displayStartTime.isAfter(now) && schedule.getEndTime().isAfter(now)) {
                log.info("[ScheduleService] 진행 중인 일정 발견 - Schedule ID: {}, Title: '{}', 표시 시작: {}, 루틴/스케줄 시작: {}, 종료: {}",
                        schedule.getId(), schedule.getTitle(), displayStartTime, actualStartTime, schedule.getEndTime());
                return Optional.of(schedule);
            }
        }

        log.debug("[ScheduleService] 진행 중인 일정 없음 - User ID: {}, 현재: {}", userId, now);
        return Optional.empty();
    }

    /**
     * 스케줄과 루틴 계산 정보를 함께 반환
     */
    public Map<String, Object> getScheduleWithRoutineInfo(Schedule schedule) {
        if (schedule.getRoutineId() == null) {
            return Map.of("schedule", schedule);
        }

        List<com.example.demo.dto.routine.CalculatedRoutineItemTime> calculatedItems =
            routineService.calculateRoutineItemTimes(schedule.getRoutineId(), schedule.getStartTime());

        if (calculatedItems.isEmpty()) {
            return Map.of("schedule", schedule);
        }

        // 루틴 시작 시간 = 첫 번째 아이템 시작 시간
        LocalDateTime routineStartTime = calculatedItems.get(0).getStartTime();

        // 루틴 종료 시간 = 마지막 아이템 종료 시간
        LocalDateTime routineEndTime = calculatedItems.get(calculatedItems.size() - 1).getEndTime();

        return Map.of(
            "schedule", schedule,
            "routineStartTime", routineStartTime,
            "routineEndTime", routineEndTime,
            "routineItems", calculatedItems
        );
    }


    //Function_Call
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

    //Function_Call
    public Schedule createScheduleByArgs(Long userId, Map<String, Object> args) {
        log.info("ScheduleService.createScheduleByArgs received args: {}", args);
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        String locationInfo = (String) args.get("location");

        // location 필드 처리 - "출발지에서 도착지" 형태를 분리
        String startLocation = null;
        String location = null;

        if (locationInfo != null && !locationInfo.trim().isEmpty()) {
            String[] locations = parseLocationInfo(locationInfo);
            if (locations.length == 2) {
                startLocation = locations[0]; // 출발지
                location = locations[1];      // 도착지
                log.info("Parsed locations - Start: {}, Destination: {}", startLocation, location);
            } else {
                // 분리되지 않으면 전체를 도착지로 처리
                location = locationInfo;
                log.info("Single location used as destination: {}", location);
            }
        }

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
                List<?> suppliesList = (List<?>) suppliesVal;
                supplies = suppliesList.stream().map(Object::toString).collect(Collectors.joining(", "));
                if (!supplies.isEmpty()) log.info("Used 'supplies' (List) field. Supplies value: '{}'", supplies);
            } catch (Exception e) { log.warn("Error processing 'supplies' as List: {}", e.getMessage()); }
        }

        if (supplies.isEmpty()) {
            Object itemsVal = args.get("items");
            if (itemsVal instanceof String && !((String) itemsVal).isEmpty()) {
                supplies = (String) itemsVal;
                log.info("Used 'items' (String) field for supplies. Supplies value: '{}'", supplies);
            } else if (itemsVal instanceof List) {
                try {
                    List<?> itemsList = (List<?>) itemsVal;
                    supplies = itemsList.stream().map(Object::toString).collect(Collectors.joining(", "));
                    if (!supplies.isEmpty()) log.info("Used 'items' (List) field for supplies. Supplies value: '{}'", supplies);
                } catch (Exception e) { log.warn("Error processing 'items' as List: {}", e.getMessage()); }
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime startTime = LocalDateTime.parse(datetime, formatter);
        LocalDateTime endTime = startTime.plusHours(1); // 기본 1시간

        // 좌표값 처리
        Double startX = args.get("startX") instanceof Number ? ((Number) args.get("startX")).doubleValue() : 0.0;
        Double startY = args.get("startY") instanceof Number ? ((Number) args.get("startY")).doubleValue() : 0.0;
        Double destinationX = args.get("destinationX") instanceof Number ? ((Number) args.get("destinationX")).doubleValue() : 0.0;
        Double destinationY = args.get("destinationY") instanceof Number ? ((Number) args.get("destinationY")).doubleValue() : 0.0;

        return createSchedule(userId, title, startTime, endTime, startLocation, location, memo, category, supplies,
                startX, startY, destinationX, destinationY);
    }

    //Function_Call
    public boolean deleteScheduleByArgs(Long userId, Map<String, Object> args) {
        log.info("ScheduleService.deleteScheduleByArgs received args: {}", args);
        
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

        // 1. 제목+시간 모두 있을 때
        if (title != null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            List<Schedule> candidates = findSchedulesByTitleAndTime(userId, title, dateTime);
            if (candidates.size() == 1) {
                Schedule schedule = candidates.get(0);
                log.info("Deleting schedule ID {} (Title: '{}') with Google Calendar sync", 
                        schedule.getId(), schedule.getTitle());
                // deleteSchedule 메서드는 내부적으로 GoogleCalendarService.deleteEvent()를 호출합니다
                deleteSchedule(userId, schedule.getId());
                return true;
            } else if (candidates.isEmpty()) {
                log.warn("No schedule found for deletion with title: '{}', datetime: {}", title, datetime);
            } else {
                log.warn("Multiple schedules found for deletion with title: '{}', datetime: {}", title, datetime);
            }
        }

        // 2. 제목만 있을 때
        if (title != null && datetime == null) {
            List<Schedule> candidates = findSchedulesByTitle(userId, title);
            if (candidates.size() == 1) {
                Schedule schedule = candidates.get(0);
                log.info("Deleting schedule ID {} (Title: '{}') with Google Calendar sync", 
                        schedule.getId(), schedule.getTitle());
                deleteSchedule(userId, schedule.getId());
                return true;
            } else if (candidates.isEmpty()) {
                log.warn("No schedule found for deletion with title: '{}'", title);
            } else {
                log.warn("Multiple schedules found for deletion with title: '{}'. Found {} schedules.", 
                        title, candidates.size());
            }
        }

        // 3. 시간만 있을 때
        if (title == null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            List<Schedule> candidates = findSchedulesByTime(userId, dateTime);
            if (candidates.size() == 1) {
                Schedule schedule = candidates.get(0);
                log.info("Deleting schedule ID {} (Title: '{}') with Google Calendar sync", 
                        schedule.getId(), schedule.getTitle());
                deleteSchedule(userId, schedule.getId());
                return true;
            } else if (candidates.isEmpty()) {
                log.warn("No schedule found for deletion at datetime: {}", datetime);
            } else {
                log.warn("Multiple schedules found for deletion at datetime: {}. Found {} schedules.", 
                        datetime, candidates.size());
            }
        }

        return false;
    }

    //Function_Call
    public Schedule updateScheduleByArgs(Long userId, Map<String, Object> args) {
        log.info("ScheduleService.updateScheduleByArgs received args: {}", args);
        
        String title = (String) args.get("title");
        String datetime = (String) args.get("datetime");
        String locationInfo = (String) args.get("location");
        
        // memo 처리 (다양한 필드명 지원)
        String memo = "";
        Object memoVal = args.get("memo");
        if (memoVal instanceof String && !((String) memoVal).isEmpty()) {
            memo = (String) memoVal;
        } else {
            Object notesVal = args.get("notes");
            if (notesVal instanceof String && !((String) notesVal).isEmpty()) {
                memo = (String) notesVal;
            } else {
                Object descriptionVal = args.get("description");
                if (descriptionVal instanceof String && !((String) descriptionVal).isEmpty()) {
                    memo = (String) descriptionVal;
                }
            }
        }
        
        String category = (String) args.getOrDefault("category", "PERSONAL");
        
        // supplies 처리
        String supplies = "";
        Object suppliesVal = args.get("supplies");
        if (suppliesVal instanceof String && !((String) suppliesVal).isEmpty()) {
            supplies = (String) suppliesVal;
        } else if (suppliesVal instanceof List) {
            try {
                List<?> suppliesList = (List<?>) suppliesVal;
                supplies = suppliesList.stream().map(Object::toString).collect(Collectors.joining(", "));
            } catch (Exception e) { 
                log.warn("Error processing 'supplies' as List: {}", e.getMessage()); 
            }
        }

        if (supplies.isEmpty()) {
            Object itemsVal = args.get("items");
            if (itemsVal instanceof String && !((String) itemsVal).isEmpty()) {
                supplies = (String) itemsVal;
            } else if (itemsVal instanceof List) {
                try {
                    List<?> itemsList = (List<?>) itemsVal;
                    supplies = itemsList.stream().map(Object::toString).collect(Collectors.joining(", "));
                } catch (Exception e) { 
                    log.warn("Error processing 'items' as List: {}", e.getMessage()); 
                }
            }
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        
        // 기존 일정 찾기 - 더 유연한 검색
        List<Schedule> candidates;
        
        if (title != null && datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            candidates = findSchedulesByTitleAndTime(userId, title, dateTime);
        } else if (title != null) {
            candidates = findSchedulesByTitle(userId, title);
        } else if (datetime != null) {
            LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
            candidates = findSchedulesByTime(userId, dateTime);
        } else {
            throw new IllegalArgumentException("수정할 일정을 식별할 수 있는 정보(제목 또는 시간)가 필요합니다.");
        }
        
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("수정할 일정을 찾을 수 없습니다.");
        }
        
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("조건에 맞는 일정이 여러 개 있습니다. 더 구체적인 정보를 제공해주세요.");
        }

        Schedule existingSchedule = candidates.get(0);
        
        // 업데이트할 값 준비 (기존 값 유지 또는 새 값 적용)
        String updatedTitle = title != null ? title : existingSchedule.getTitle();
        LocalDateTime updatedStartTime = datetime != null ? LocalDateTime.parse(datetime, formatter) : existingSchedule.getStartTime();
        LocalDateTime updatedEndTime = datetime != null ? updatedStartTime.plusHours(1) : existingSchedule.getEndTime();
        
        // location 필드 처리 - "출발지에서 도착지" 형태를 분리
        String startLocation = existingSchedule.getStartLocation();
        String location = existingSchedule.getLocation();
        
        if (locationInfo != null && !locationInfo.trim().isEmpty()) {
            String[] locations = parseLocationInfo(locationInfo);
            if (locations.length == 2) {
                startLocation = locations[0]; // 출발지
                location = locations[1];      // 도착지
                log.info("Parsed locations for update - Start: {}, Destination: {}", startLocation, location);
            } else {
                // 분리되지 않으면 전체를 도착지로 처리
                location = locationInfo;
                log.info("Single location used as destination for update: {}", location);
            }
        }
        
        String updatedMemo = memo.isEmpty() ? existingSchedule.getMemo() : memo;
        String updatedSupplies = supplies.isEmpty() ? existingSchedule.getSupplies() : supplies;

        // 좌표값 처리 (있으면 사용, 없으면 기존 값 유지)
        Double startX = args.get("startX") instanceof Number ? ((Number) args.get("startX")).doubleValue() : 
                        (existingSchedule.getStartX() != null ? existingSchedule.getStartX() : 0.0);
        Double startY = args.get("startY") instanceof Number ? ((Number) args.get("startY")).doubleValue() : 
                        (existingSchedule.getStartY() != null ? existingSchedule.getStartY() : 0.0);
        Double destinationX = args.get("destinationX") instanceof Number ? ((Number) args.get("destinationX")).doubleValue() : 
                              (existingSchedule.getDestinationX() != null ? existingSchedule.getDestinationX() : 0.0);
        Double destinationY = args.get("destinationY") instanceof Number ? ((Number) args.get("destinationY")).doubleValue() : 
                              (existingSchedule.getDestinationY() != null ? existingSchedule.getDestinationY() : 0.0);

        log.info("Updating schedule ID {} with Google Calendar sync", existingSchedule.getId());
        
        // updateSchedule 메서드는 내부적으로 GoogleCalendarService.updateEvent()를 호출합니다
        return updateSchedule(
            userId,
            existingSchedule.getId(),
            existingSchedule.getRoutineId(), // 기존 routineId 유지
            updatedTitle,
            updatedStartTime,
            updatedEndTime,
            startLocation,
            startX,
            startY,
            location,
            destinationX,
            destinationY,
            updatedMemo,
            updatedSupplies,
            category
        );
    }

    /**
     * "출발지에서 도착지" 형태의 문자열을 파싱하여 [출발지, 도착지] 배열로 반환
     */
    private String[] parseLocationInfo(String locationInfo) {
        if (locationInfo == null || locationInfo.trim().isEmpty()) {
            return new String[0];
        }

        String trimmed = locationInfo.trim();

        // "에서" 패턴으로 분리
        if (trimmed.contains("에서")) {
            String[] parts = trimmed.split("에서", 2);
            if (parts.length == 2) {
                String startLocation = parts[0].trim();
                String endLocation = parts[1].trim();

                // "로" 또는 "으로" 제거
                endLocation = endLocation.replaceAll("(으)?로$", "").trim();

                return new String[]{startLocation, endLocation};
            }
        }

        // "에서" 패턴이 없으면 다른 패턴들도 시도
        String[] separators = {"→", "->", "~", " to ", " - "};
        for (String separator : separators) {
            if (trimmed.contains(separator)) {
                String[] parts = trimmed.split(separator, 2);
                if (parts.length == 2) {
                    return new String[]{parts[0].trim(), parts[1].trim()};
                }
            }
        }

        // 분리할 수 없으면 빈 배열 반환
        return new String[0];
    }

    /**
     * 날씨 업데이트 대상 활성 스케줄 조회 (진행 중이거나 24시간 이내 시작 예정)
     */
    @Transactional(readOnly = true)
    public List<Schedule> getActiveSchedulesForWeatherUpdate(LocalDateTime now) {
        LocalDateTime startRange = now.minusHours(1); // 1시간 전부터 (진행 중인 것 포함)
        LocalDateTime endRange = now.plusHours(24); // 24시간 후까지

        return scheduleRepository.findActiveSchedulesForWeatherUpdate(startRange, endRange, now);
    }
}
