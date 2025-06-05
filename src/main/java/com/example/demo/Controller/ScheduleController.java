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

    @PostMapping("/ai-function")
    public ResponseEntity<?> handleAIFunction(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody Map<String, Object> payload) {
        // function_call 객체 추출
        Map<String, Object> functionCall = (Map<String, Object>) payload.get("function_call");
        String functionName = (String) functionCall.get("name");
        Map<String, Object> args = (Map<String, Object>) functionCall.get("arguments");
        Long userId = appUser.getId();

        try {
            switch (functionName) {
                case "create_schedule":
                    Schedule created = scheduleService.createScheduleByArgs(userId, args);
                    if (created == null || created.getId() == null) {
                        return ResponseEntity.status(500).body("일정 저장에 실패했습니다.");
                    }
                    return ResponseEntity.ok(Map.of(
                        "message", "일정이 성공적으로 등록되었습니다.",
                        "data", created
                    ));
                case "get_schedule":
                    List<Schedule> found = scheduleService.findSchedulesByArgs(userId, args);
                    String msg;
                    if (found.isEmpty()) {
                        msg = "해당 날짜에 일정이 없습니다.";
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("조회된 일정 목록입니다:\n");
                        for (Schedule s : found) {
                            sb.append("- ").append(s.getTitle());
                            if (s.getStartTime() != null) {
                                sb.append(" (").append(s.getStartTime());
                                if (s.getEndTime() != null) sb.append(" ~ ").append(s.getEndTime());
                                sb.append(")");
                            }
                            if (s.getLocation() != null) sb.append(" @ ").append(s.getLocation());
                            sb.append("\n");
                        }
                        msg = sb.toString();
                    }
                    return ResponseEntity.ok(Map.of(
                        "message", msg,
                        "data", found
                    ));
                case "delete_schedule":
                    boolean deleted = scheduleService.deleteScheduleByArgs(userId, args);
                    return ResponseEntity.ok(Map.of("message", deleted ? "일정이 삭제되었습니다." : "일정 삭제에 실패했습니다.", "deleted", deleted));
                case "update_schedule":
                    Schedule updated = scheduleService.updateScheduleByArgs(userId, args);
                    return ResponseEntity.ok(Map.of(
                        "message", "일정이 성공적으로 수정되었습니다.",
                        "data", updated
                    ));
                default:
                    return ResponseEntity.badRequest().body("지원하지 않는 function_call");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("일정 처리 중 오류: " + e.getMessage());
        }
    }

}