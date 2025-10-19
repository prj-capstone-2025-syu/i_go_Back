package com.example.demo.controller;

import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.user.User;
import com.example.demo.handler.NotificationWebSocketHandler;
import com.example.demo.repository.ScheduleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FCMService;
import com.example.demo.service.ScheduleNotificationService;
import com.example.demo.service.ScheduleWeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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
    private final FCMService fcmService;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final NotificationWebSocketHandler webSocketHandler;

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

    /**
     * 교통 지연 알림 강제 발생 (테스트용)
     * 특정 일정에 대해 교통 지연 알림을 강제로 발생시킵니다.
     */
    @PostMapping("/force-traffic-delay")
    public ResponseEntity<Map<String, Object>> forceTrafficDelayNotification(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam Long scheduleId,
            @RequestParam(defaultValue = "30") int delayMinutes) {

        log.info("🧪 [DebugController] 교통 지연 알림 강제 발생 - 사용자 ID: {}, 일정 ID: {}, 지연: {}분",
                appUser.getId(), scheduleId, delayMinutes);

        try {
            Schedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. ID: " + scheduleId));

            if (!schedule.getUser().getId().equals(appUser.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "해당 일정에 대한 권한이 없습니다."
                ));
            }

            User user = userRepository.findById(appUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "message", "FCM 토큰이 없습니다. 알림을 받을 수 없습니다."
                ));
            }

            String originalTitle = schedule.getTitle();

            // ScheduleNotificationService의 스케줄 조정 메서드 재사용
            LocalDateTime originalStartTime = scheduleNotificationService.adjustScheduleForTrafficDelay(schedule, delayMinutes);

            // ScheduleNotificationService의 알림 전송 메서드 재사용
            scheduleNotificationService.sendTrafficDelayNotification(schedule, user, "테스트", delayMinutes);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "교통 체증 알림이 전송되었습니다.");
            response.put("scheduleId", scheduleId);
            response.put("delayMinutes", delayMinutes);
            response.put("originalTitle", originalTitle);
            response.put("newTitle", schedule.getTitle());
            response.put("originalStartTime", originalStartTime.toString());
            response.put("newStartTime", schedule.getStartTime().toString());
            response.put("timeAdjusted", delayMinutes + "분 앞당김");
            response.put("timestamp", LocalDateTime.now());

            log.info("✅ [DebugController] 교통 체증 알림 전송 완료 - 일정 ID: {}, 시간 조정: {}분 앞당김", scheduleId, delayMinutes);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 교통 지연 알림 전송 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "교통 지연 알림 전송 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * 비 알림 강제 발생 (테스트용)
     * 특정 일정에 대해 비 오는 날씨 알림을 강제로 발생시킵니다.
     */
    @PostMapping("/force-rain-alert")
    public ResponseEntity<Map<String, Object>> forceRainAlert(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam Long scheduleId) {

        log.info("🧪 [DebugController] 비 알림 강제 발생 - 사용자 ID: {}, 일정 ID: {}",
                appUser.getId(), scheduleId);

        try {
            Schedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. ID: " + scheduleId));

            if (!schedule.getUser().getId().equals(appUser.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "해당 일정에 대한 권한이 없습니다."
                ));
            }

            User user = userRepository.findById(appUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "message", "FCM 토큰이 없습니다. 알림을 받을 수 없습니다."
                ));
            }

            String originalTitle = schedule.getTitle();
            String originalSupplies = schedule.getSupplies();

            // ScheduleNotificationService의 스케줄 조정 메서드 재사용
            LocalDateTime originalStartTime = scheduleNotificationService.adjustScheduleForWeather(schedule);

            // ScheduleNotificationService의 알림 전송 메서드 재사용
            scheduleNotificationService.sendWeatherAlertNotification(schedule, user, "비");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "비 알림이 전송되었습니다.");
            response.put("scheduleId", scheduleId);
            response.put("originalTitle", originalTitle);
            response.put("newTitle", schedule.getTitle());
            response.put("originalStartTime", originalStartTime.toString());
            response.put("newStartTime", schedule.getStartTime().toString());
            response.put("timeAdjusted", "15분 앞당김");
            response.put("originalSupplies", originalSupplies);
            response.put("newSupplies", schedule.getSupplies());
            response.put("timestamp", LocalDateTime.now());

            log.info("✅ [DebugController] 비 알림 전송 완료 - 일정 ID: {}, 시간 조정: 15분 앞당김, 우산 추가", scheduleId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 비 알림 전송 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "비 알림 전송 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * 사용자의 모든 일정 목록 조회 (테스트용)
     */
    @GetMapping("/my-schedules")
    public ResponseEntity<Map<String, Object>> getMySchedules(
            @AuthenticationPrincipal AppUser appUser) {

        log.info("🧪 [DebugController] 일정 목록 조회 - 사용자 ID: {}", appUser.getId());

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime weekLater = now.plusDays(7);

            List<Schedule> schedules = scheduleRepository.findByUserIdAndStartTimeBetween(
                    appUser.getId(), now, weekLater);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", schedules.size());
            response.put("schedules", schedules.stream().map(s -> {
                Map<String, Object> scheduleMap = new HashMap<>();
                scheduleMap.put("id", s.getId());
                scheduleMap.put("title", s.getTitle());
                scheduleMap.put("startTime", s.getStartTime());
                scheduleMap.put("endTime", s.getEndTime());
                scheduleMap.put("location", s.getLocation());
                scheduleMap.put("status", s.getStatus());
                scheduleMap.put("hasCoordinates",
                        s.getStartX() != null && s.getStartY() != null &&
                                s.getDestinationX() != null && s.getDestinationY() != null);
                return scheduleMap;
            }).toList());
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 일정 목록 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "일정 목록 조회 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * 일정 제목의 플래그 제거 (테스트 후 원복용)
     */
    @PostMapping("/remove-schedule-flag")
    public ResponseEntity<Map<String, Object>> removeScheduleFlag(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam Long scheduleId) {

        log.info("🧪 [DebugController] 일정 플래그 제거 - 사용자 ID: {}, 일정 ID: {}",
                appUser.getId(), scheduleId);

        try {
            Schedule schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. ID: " + scheduleId));

            if (!schedule.getUser().getId().equals(appUser.getId())) {
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "해당 일정에 대한 권한이 없습니다."
                ));
            }

            String originalTitle = schedule.getTitle();
            String newTitle = originalTitle
                    .replace("[교통체증] ", "")
                    .replace("[기상악화] ", "");

            schedule.setTitle(newTitle);
            scheduleRepository.save(schedule);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "일정 플래그가 제거되었습니다.");
            response.put("scheduleId", scheduleId);
            response.put("originalTitle", originalTitle);
            response.put("newTitle", newTitle);
            response.put("timestamp", LocalDateTime.now());

            log.info("✅ [DebugController] 일정 플래그 제거 완료 - 일정 ID: {}", scheduleId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] 일정 플래그 제거 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "일정 플래그 제거 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * WebSocket으로 테스트 알림 전송 (테스트용)
     * 현재 로그인한 사용자에게 WebSocket을 통해 테스트 알림을 전송합니다.
     */
    @PostMapping("/send-websocket-notification")
    public ResponseEntity<Map<String, Object>> sendWebSocketNotification(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam(defaultValue = "GENERIC") String type,
            @RequestParam(defaultValue = "테스트 알림") String title,
            @RequestParam(defaultValue = "WebSocket 테스트 메시지입니다.") String body) {

        log.info("🧪 [DebugController] WebSocket 알림 전송 테스트 - 사용자 ID: {}, 타입: {}", 
                appUser.getId(), type);

        try {
            // User 엔티티에서 이메일 가져오기
            User user = userRepository.findById(appUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            String userEmail = user.getEmail();
            
            // WebSocket 연결 여부 확인 (이메일로)
            boolean isConnected = webSocketHandler.isUserConnected(userEmail);
            
            if (!isConnected) {
                log.warn("⚠️ [DebugController] 사용자의 WebSocket이 연결되어 있지 않습니다 - userEmail: {}", userEmail);
                return ResponseEntity.status(400).body(Map.of(
                        "success", false,
                        "message", "WebSocket이 연결되어 있지 않습니다. 먼저 WebSocket을 연결해주세요.",
                        "isConnected", false,
                        "userEmail", userEmail,
                        "activeConnections", webSocketHandler.getActiveConnectionCount()
                ));
            }

            // 알림 데이터 구성
            Map<String, String> notificationData = new HashMap<>();
            notificationData.put("type", type);
            notificationData.put("title", title);
            notificationData.put("body", body);
            notificationData.put("timestamp", LocalDateTime.now().toString());
            notificationData.put("scheduleId", "999"); // 테스트용 더미 ID

            // WebSocket을 통해 알림 전송 (이메일로)
            webSocketHandler.sendNotificationToUser(userEmail, notificationData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "WebSocket 알림이 전송되었습니다.");
            response.put("userId", appUser.getId());
            response.put("userEmail", userEmail);
            response.put("type", type);
            response.put("title", title);
            response.put("body", body);
            response.put("isConnected", true);
            response.put("activeConnections", webSocketHandler.getActiveConnectionCount());
            response.put("timestamp", LocalDateTime.now());

            log.info("✅ [DebugController] WebSocket 알림 전송 완료 - userEmail: {}", userEmail);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] WebSocket 알림 전송 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "WebSocket 알림 전송 실패: " + e.getMessage()
            ));
        }
    }

    /**
     * WebSocket 연결 상태 확인 (테스트용)
     */
    @GetMapping("/websocket-status")
    public ResponseEntity<Map<String, Object>> getWebSocketStatus(
            @AuthenticationPrincipal AppUser appUser) {

        log.info("🧪 [DebugController] WebSocket 상태 확인 - 사용자 ID: {}", appUser.getId());

        try {
            // User 엔티티에서 이메일 가져오기
            User user = userRepository.findById(appUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            String userEmail = user.getEmail();
            
            boolean isConnected = webSocketHandler.isUserConnected(userEmail);
            int activeConnections = webSocketHandler.getActiveConnectionCount();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", appUser.getId());
            response.put("userEmail", userEmail);
            response.put("isConnected", isConnected);
            response.put("activeConnections", activeConnections);
            response.put("timestamp", LocalDateTime.now());

            log.info("✅ [DebugController] WebSocket 상태 - userEmail: {}, 연결됨: {}, 전체 연결 수: {}", 
                    userEmail, isConnected, activeConnections);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ [DebugController] WebSocket 상태 확인 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "WebSocket 상태 확인 실패: " + e.getMessage()
            ));
        }
    }
}
