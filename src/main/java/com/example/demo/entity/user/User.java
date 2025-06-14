package com.example.demo.entity.user;

import com.example.demo.entity.entityInterface.AppUser;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

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

    @Column(length = 1000) // 애플리케이션 자체 Refresh Token
    private String appRefreshToken;

    private LocalDateTime appRefreshTokenExpiresAt; // 애플리케이션 자체 Refresh Token 만료 시간

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
        return "ROLE_USER"; // 기본 역할
    }

    // Spring Security UserDetails 인터페이스의 getAuthorities() 구현
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(this.getRole()));
    }
}