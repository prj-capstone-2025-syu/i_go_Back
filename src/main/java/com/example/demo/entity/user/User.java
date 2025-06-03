package com.example.demo.entity.user;

import com.example.demo.entity.entityInterface.AppUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false, unique = true)
    private String oauthId; // 구글 sub 값

    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    private LocalDateTime registeredAt;

    private LocalDateTime lastLoginAt;

    // Google Calendar API를 위한 필드들 추가
    @Column(length = 1000)
    private String googleAccessToken;

    @Column(length = 1000)
    private String googleRefreshToken;

    private LocalDateTime googleTokenExpiresAt;

    @Column
    private String fcmToken;

    // 알림 설정 필드
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notificationsEnabled = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notifyTodaySchedule = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notifyNextSchedule = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notifyRoutineProgress = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notifySupplies = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notifyUnexpectedEvent = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    @Builder.Default
    private boolean notifyAiFeature = true; // AI 기능 관련 알림 (예: 읽어주기)

    @Override
    public String getRole() {
        return "ROLE_USER";
    }
}