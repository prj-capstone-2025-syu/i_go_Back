package com.example.demo.service;

import com.example.demo.dto.notification.NotificationDto;
import com.example.demo.entity.fcm.Notification;
import com.example.demo.entity.user.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public List<NotificationDto> getNotificationsForUser(Long userId, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        Pageable pageable = PageRequest.of(0, limit); // 첫 페이지부터 limit 개수만큼
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        return notifications.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private NotificationDto convertToDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .body(notification.getBody())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .relatedId(notification.getRelatedId())
                .notificationType(notification.getNotificationType())
                .build();
    }
}