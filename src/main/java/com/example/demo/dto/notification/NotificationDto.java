package com.example.demo.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private String title;
    private String body;
    private boolean isRead;
    private LocalDateTime createdAt;
    private Long relatedId; // 알림과 관련된 엔티티 ID (예: 스케줄 ID)
    private String notificationType; // 알림 유형 (예: "SCHEDULE_START")
}