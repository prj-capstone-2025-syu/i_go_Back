package com.example.demo.Controller;

import com.example.demo.dto.NotificationDto;
import com.example.demo.entity.entityInterface.AppUser; // 추가
import com.example.demo.entity.fcm.Notification;
import com.example.demo.entity.user.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest; // 추가
import org.springframework.data.domain.Pageable; // 추가
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal; // 추가
import org.springframework.web.bind.annotation.*;
import com.example.demo.service.FCMService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final FCMService fcmService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<Notification>> getUserNotifications(@AuthenticationPrincipal AppUser appUser) {
        User user = userRepository.findById(appUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + appUser.getId()));
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Notification>> getUnreadNotifications(@AuthenticationPrincipal AppUser appUser) {
        User user = userRepository.findById(appUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + appUser.getId()));
        List<Notification> notifications = notificationRepository.findByUserAndIsReadOrderByCreatedAtDesc(user, false);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal AppUser appUser, @PathVariable Long id) {
        User user = userRepository.findById(appUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + appUser.getId()));
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. ID: " + id));

        if (!notification.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build(); // Forbidden
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recent")
    public ResponseEntity<List<Notification>> getRecentNotifications(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam(defaultValue = "10") int limit) {
        User user = userRepository.findById(appUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + appUser.getId()));
        Pageable pageable = PageRequest.of(0, limit);
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendNotification(@RequestParam String token,
                                                   @RequestParam String title,
                                                   @RequestParam String body) {
        Map<String, String> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");

        String response = fcmService.sendMessageToToken(token, title, body, data);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<NotificationDto>> getRecentNotifications(
            @AuthenticationPrincipal User user, // Spring Security를 통해 인증된 사용자 정보를 가져옵니다.
            @RequestParam(defaultValue = "7") int limit) { // 기본값을 7로 설정합니다.
        if (user == null) {
            // 사용자가 인증되지 않은 경우, 적절한 응답을 반환합니다. (예: 401 Unauthorized)
            return ResponseEntity.status(401).build();
        }
        // NotificationService를 호출하여 사용자의 최근 알림을 가져옵니다.
        List<NotificationDto> notifications = notificationService.getNotificationsForUser(user.getId(), limit);
        return ResponseEntity.ok(notifications);
    }


}