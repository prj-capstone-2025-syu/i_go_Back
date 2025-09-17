package com.example.demo.controller;

import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.entity.user.User;
import com.example.demo.service.ScheduleNotificationService;
import com.example.demo.service.ScheduleWeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 테스트 및 디버깅용 컨트롤러
 * 실제 운영 환경에서는 제거하거나 접근 권한을 제한해야 함
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final ScheduleNotificationService scheduleNotificationService;
    private final ScheduleWeatherService scheduleWeatherService;

    /**
     * 현재 시간에 알림 처리 로직을 수동으로 실행
     * 1시간 전 알림, 스케줄 시작 알림, 루틴 아이템 알림 등을 테스트할 수 있음
     */
    @PostMapping("/trigger-notifications")
    public ResponseEntity<Map<String, Object>> triggerNotifications() {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        log.info("🧪 [DebugController] 수동 알림 트리거 실행 - 현재 시간: {}", now);

        try {
            // 알림 처리 로직을 수동으로 실행
            scheduleNotificationService.sendScheduleAndRoutineNotifications();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "알림 처리 로직이 수동으로 실행되었습니다.");
            response.put("timestamp", now);
            response.put("success", true);

            log.info("✅ [DebugController] 수동 알림 트리거 완료");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 수동 알림 트리거 실패: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "알림 처리 중 오류가 발생했습니다: " + e.getMessage());
            response.put("timestamp", now);
            response.put("success", false);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 날씨 캐시 업데이트를 수동으로 실행
     */
    @PostMapping("/update-weather-cache")
    public ResponseEntity<Map<String, Object>> updateWeatherCache() {
        log.info("🧪 [DebugController] 수동 날씨 캐시 업데이트 실행");

        try {
            scheduleWeatherService.updateScheduleWeatherInfo();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "날씨 캐시가 수동으로 업데이트되었습니다.");
            response.put("timestamp", LocalDateTime.now());
            response.put("success", true);

            log.info("✅ [DebugController] 수동 날씨 캐시 업데이트 완료");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 수동 날씨 캐시 업데이트 실패: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "날씨 캐시 업데이트 중 오류가 발생했습니다: " + e.getMessage());
            response.put("timestamp", LocalDateTime.now());
            response.put("success", false);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 특정 시간으로 설정하여 알림 테스트 (시뮬레이션)
     * 예: 현재 시간을 1시간 전으로 설정하여 1시간 전 알림이 동작하는지 확인
     */
    @PostMapping("/simulate-time")
    public ResponseEntity<Map<String, Object>> simulateTimeBasedNotifications(
            @RequestParam int hoursOffset,
            @RequestParam(defaultValue = "0") int minutesOffset) {

        LocalDateTime simulatedTime = LocalDateTime.now()
                .plusHours(hoursOffset)
                .plusMinutes(minutesOffset)
                .withSecond(0)
                .withNano(0);

        log.info("🧪 [DebugController] 시간 시뮬레이션 실행 - 시뮬레이션 시간: {}", simulatedTime);
        log.info("📝 [DebugController] 오프셋: {}시간 {}분", hoursOffset, minutesOffset);

        try {
            // 실제로는 시간을 변경할 수 없으므로, 로그만 출력하고 현재 로직 실행
            log.warn("⚠️ [DebugController] 주의: 실제 시간은 변경되지 않습니다. 현재 시간으로 알림 로직을 실행합니다.");

            scheduleNotificationService.sendScheduleAndRoutineNotifications();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "시뮬레이션된 시간으로 알림 처리가 실행되었습니다.");
            response.put("simulatedTime", simulatedTime);
            response.put("actualTime", LocalDateTime.now());
            response.put("success", true);
            response.put("note", "실제 시간은 변경되지 않습니다. 테스트를 위해서는 실제 데이터의 시간을 조정해주세요.");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 시간 시뮬레이션 실패: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "시뮬레이션 실행 중 오류가 발생했습니다: " + e.getMessage());
            response.put("simulatedTime", simulatedTime);
            response.put("success", false);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 현재 사용자의 진행 중인/다가오는 스케줄 정보 확인
     */
    @GetMapping("/schedule-status")
    public ResponseEntity<Map<String, Object>> getScheduleStatus(@AuthenticationPrincipal AppUser appUser) {
        log.info("🧪 [DebugController] 스케줄 상태 확인 - 사용자 ID: {}", appUser.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("userId", appUser.getId());
        response.put("timestamp", LocalDateTime.now());

        try {
            // 진행 중인 스케줄과 다가오는 스케줄의 날씨 정보 확인
            scheduleWeatherService.getCurrentScheduleWithWeather(appUser.getId())
                    .subscribe(currentSchedule -> {
                        if (currentSchedule.isPresent()) {
                            response.put("currentSchedule", currentSchedule.get());
                            log.info("📊 [DebugController] 진행 중인 스케줄 발견: {}", currentSchedule.get().getScheduleId());
                        } else {
                            response.put("currentSchedule", null);
                            log.info("📊 [DebugController] 진행 중인 스케줄 없음");
                        }
                    });

            scheduleWeatherService.getNextScheduleWithWeather(appUser.getId())
                    .subscribe(nextSchedule -> {
                        if (nextSchedule.isPresent()) {
                            response.put("nextSchedule", nextSchedule.get());
                            log.info("📊 [DebugController] 다가오는 스케줄 발견: {}", nextSchedule.get().getScheduleId());
                        } else {
                            response.put("nextSchedule", null);
                            log.info("📊 [DebugController] 다가오는 스케줄 없음");
                        }
                    });

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 스케줄 상태 확인 실패: {}", e.getMessage(), e);

            response.put("message", "스케줄 상태 확인 중 오류가 발생했습니다: " + e.getMessage());
            response.put("success", false);

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * 로그 레벨 확인 및 테스트
     */
    @GetMapping("/log-test")
    public ResponseEntity<Map<String, String>> testLogs() {
        log.error("🔴 [DebugController] ERROR 레벨 로그 테스트");
        log.warn("🟡 [DebugController] WARN 레벨 로그 테스트");
        log.info("🔵 [DebugController] INFO 레벨 로그 테스트");
        log.debug("🟣 [DebugController] DEBUG 레벨 로그 테스트");
        log.trace("⚪ [DebugController] TRACE 레벨 로그 테스트");

        Map<String, String> response = new HashMap<>();
        response.put("message", "로그 레벨 테스트가 실행되었습니다. 콘솔을 확인해주세요.");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }
}
