package com.example.demo.service;

import com.example.demo.entity.routine.Routine;
import com.example.demo.entity.routine.RoutineItem;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.schedule.Category;
import com.example.demo.entity.user.User;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.ScheduleRepository;
import com.example.demo.repository.UserRepository;
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
    private final UserRepository userRepository; // UserRepository 주입

    // 루틴 기반 일정 생성
    public Schedule createFromRoutine(Long userId, Long routineId, String title, LocalDateTime startTime,
                                      String location, String memo, String category) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        Routine routine = routineRepository.findById(routineId)
                .orElseThrow(() -> new IllegalArgumentException("해당 루틴이 존재하지 않습니다: " + routineId));

        if (!routine.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 루틴에 대한 권한이 없습니다.");
        }

        int totalMinutes = routine.getItems().stream()
                .mapToInt(RoutineItem::getDurationMinutes)
                .sum();

        LocalDateTime endTime = startTime.plusMinutes(totalMinutes);

        Schedule schedule = Schedule.builder()
                .title(title)
                .startTime(startTime)
                .endTime(endTime)
                .location(location)
                .memo(memo)
                .category(Category.valueOf(category.toUpperCase())) // Enum 변환 시 대문자로
                .routineId(routineId)
                .user(user) // 조회한 User 객체 사용
                .status(Schedule.ScheduleStatus.PENDING)
                .build();

        try {
            // GoogleCalendarService에 userId 전달
            String eventId = googleCalendarService.createEvent(schedule, userId);
            schedule.setGoogleCalendarEventId(eventId);
        } catch (Exception e) {
            throw new RuntimeException("구글 캘린더 이벤트 생성 실패: " + e.getMessage(), e);
        }

        return scheduleRepository.save(schedule);
    }

    // 날짜별 일정 조회
    @Transactional(readOnly = true) // 조회 메서드이므로 readOnly true
    public List<Schedule> getSchedulesByDateRange(Long userId, LocalDateTime start, LocalDateTime end) {
        return scheduleRepository.findByUserIdAndStartTimeBetween(userId, start, end);
    }

    // 일정 상태 업데이트 (이 메서드는 특정 사용자가 아닌 전체 스케줄 대상이므로 userId 파라미터 불필요)
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
    public void deleteSchedule(Long userId, Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("삭제할 일정을 찾을 수 없습니다. ID: " + scheduleId));

        if (!schedule.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 삭제 권한이 없습니다.");
        }

        if (schedule.getGoogleCalendarEventId() != null && !schedule.getGoogleCalendarEventId().isEmpty()) {
            try {
                // GoogleCalendarService에 userId 전달
                googleCalendarService.deleteEvent(schedule.getGoogleCalendarEventId(), userId);
            } catch (Exception e) {
                // 구글 캘린더 이벤트 삭제 실패 시 로깅 또는 예외 처리를 할 수 있으나, DB 삭제는 진행되도록 할 수 있음
                // 여기서는 RuntimeException으로 처리
                throw new RuntimeException("구글 캘린더 이벤트 삭제 실패: " + e.getMessage(), e);
            }
        }
        scheduleRepository.delete(schedule);
    }
}