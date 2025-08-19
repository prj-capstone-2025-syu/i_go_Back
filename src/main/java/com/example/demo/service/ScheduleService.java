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

    //Function_Call
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

    //Function_Call
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
