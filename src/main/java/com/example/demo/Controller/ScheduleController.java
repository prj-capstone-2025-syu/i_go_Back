package com.example.demo.Controller;

import com.example.demo.dto.Schedule.CreateScheduleRequest;
import com.example.demo.dto.Schedule.UpdateScheduleRequest;
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
import java.util.Optional;


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
                request.getEndTime(),
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

    // 특정 일정 조회
    @GetMapping("/{scheduleId}")
    public ResponseEntity<Schedule> getSchedule(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long scheduleId) {
        Schedule schedule = scheduleService.getScheduleById(appUser.getId(), scheduleId);
        return ResponseEntity.ok(schedule);
    }

    // 일정 수정
    @PutMapping("/{scheduleId}")
    public ResponseEntity<Schedule> updateSchedule(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long scheduleId,
            @RequestBody UpdateScheduleRequest request) {
        Schedule schedule = scheduleService.updateSchedule(
                appUser.getId(),
                scheduleId,
                request.getRoutineId(),
                request.getTitle(),
                request.getStartTime(),
                request.getEndTime(),
                request.getLocation(),
                request.getMemo(),
                request.getSupplies(),
                request.getCategory()
        );
        return ResponseEntity.ok(schedule);
    }

    //촤근 3개 불러오기
    @GetMapping("/upcoming")
    public ResponseEntity<List<Schedule>> getUpcomingSchedules(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam(defaultValue = "3") int limit) {
        List<Schedule> schedules = scheduleService.getUpcomingSchedules(appUser.getId(), limit);
        return ResponseEntity.ok(schedules);
    }

    // 진행 중인 가장 최근 일정 1개 조회 API
    @GetMapping("/in-progress/latest")
    public ResponseEntity<?> getLatestInProgressSchedule(@AuthenticationPrincipal AppUser appUser) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        Optional<Schedule> scheduleOpt = scheduleService.getLatestInProgressSchedule(appUser.getId());
        // Optional이 비어있으면 null을, 아니면 Schedule 객체를 반환
        // 클라이언트에서는 null을 통해 진행 중인 일정이 없음을 판단
        return scheduleOpt.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(null));
    }

}