package com.example.demo.Controller;

import com.example.demo.dto.CreateScheduleRequest;
import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<Schedule> createSchedule(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody CreateScheduleRequest request) {
        Schedule schedule = scheduleService.createFromRoutine(
                appUser.getId(), // 인증된 사용자 ID 사용
                request.getRoutineId(),
                request.getTitle(),
                request.getStartTime(),
                request.getLocation(),
                request.getMemo(),
                request.getSupplies(),
                request.getCategory()
        );
        return ResponseEntity.ok(schedule);
    }

    @GetMapping
    public ResponseEntity<List<Schedule>> getSchedules(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<Schedule> schedules = scheduleService.getSchedulesByDateRange(appUser.getId(), start, end); // 인증된 사용자 ID 사용
        return ResponseEntity.ok(schedules);
    }

    @DeleteMapping("/{scheduleId}")
    public ResponseEntity<?> deleteSchedule(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(appUser.getId(), scheduleId); // 인증된 사용자 ID 사용
        return ResponseEntity.ok(Map.of("message", "일정이 삭제되었습니다."));
    }
}