package com.example.demo.entity.fcm;

import com.example.demo.entity.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean isRead = false;

    @CreationTimestamp // 엔티티가 생성될 때 자동으로 현재 시간 저장
    private LocalDateTime createdAt;

    private Long relatedId; // 관련 엔티티의 ID (예: 스케줄 ID)
    private String notificationType; // 알림 타입 (예: "SCHEDULE_START", "NEW_MESSAGE")
}