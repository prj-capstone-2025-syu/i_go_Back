package com.example.demo.service;

import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.schedule.Category;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final RoutineRepository routineRepository;
    private final GoogleCalendarService googleCalendarService;

    // 루틴 기반 일정 생성
    public Schedule createFromRoutine(Long routineId, String title, LocalDateTime startTime,
                                      String location, String memo, String category, Long userId) {
        // 루틴 조회
        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("해당 루틴이 존재하지 않습니다: " + routineId));

        // 루틴 총 소요시간 계산
        int totalMinutes = routine.getItems().stream()
                .mapToInt(RoutineItem::getDurationMinutes)
                .sum();

        // 종료 시간 계산
        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        // Schedule 생성
        Schedule schedule = Schedule.builder()
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .memo(memo)
                .category(Category.valueOf(category))
                .routineId(routineId)
                .user(routine.getUser())
                .status(Schedule.ScheduleStatus.PENDING)
                .build();

        // 구글 캘린더에 이벤트 생성
        try {
            String eventId = googleCalendarService.createEvent(schedule, userId.toString());
            schedule.setGoogleCalendarEventId(eventId);
        } catch (Exception e) {
            // 오류 처리
            throw new RuntimeException("구글 캘린더 이벤트 생성 실패", e);
        }

        // DB에 저장
        return scheduleRepository.save(schedule);
    }

    // 날짜별 일정 조회
    public List<Schedule> getSchedulesByDateRange(LocalDateTime start, LocalDateTime end, Long userId) {
        return scheduleRepository.findByUserIdAndStartTimeBetween(userId, start, end);
    }

    // 일정 상태 업데이트
    public void updateScheduleStatus() {
        LocalDateTime now = LocalDateTime.now();
        List<Schedule> allSchedules = scheduleRepository.findAll();

        for (Schedule schedule : allSchedules) {
            if (now.isBefore(schedule.getStartTime())) {
                schedule.setStatus(Schedule.ScheduleStatus.PENDING);
            } else if (now.isAfter(schedule.getEndTime())) {
                schedule.setStatus(Schedule.ScheduleStatus.COMPLETED);
            } else {
                schedule.setStatus(Schedule.ScheduleStatus.IN_PROGRESS);
            }
        }

        scheduleRepository.saveAll(allSchedules);
    }

    // 일정 삭제
    public void deleteSchedule(Long scheduleId) {
        Optional<Schedule> scheduleOpt = scheduleRepository.findById(scheduleId);
        if (scheduleOpt.isPresent()) {
            Schedule schedule = scheduleOpt.get();
            // 구글 캘린더에서 이벤트 삭제
            try {
                googleCalendarService.deleteEvent(schedule.getGoogleCalendarEventId(),
                        schedule.getUser().getId().toString());
            } catch (Exception e) {
                throw new RuntimeException("구글 캘린더 이벤트 삭제 실패", e);
            }
            // DB에서 일정 삭제
            scheduleRepository.delete(schedule);
        }
    }
}