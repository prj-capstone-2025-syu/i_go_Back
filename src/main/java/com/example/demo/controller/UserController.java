package com.example.demo.controller;

import com.example.demo.dto.token.FcmTokenRequest;
import com.example.demo.dto.notification.NotificationSettingsDto;
import com.example.demo.entity.user.User;
import com.example.demo.dto.user.UserUpdateRequestDto;
import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 현재 로그인한 사용자 정보 조회
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(@AuthenticationPrincipal AppUser appUser) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        // AppUser에서 직접 정보를 가져오거나, 필요시 ID로 User 엔티티를 조회하여 반환
        return ResponseEntity.ok(Map.of(
                "id", appUser.getId(),
                "email", appUser.getEmail(),
                "nickname", userService.getUserNickname(appUser.getId()), // 닉네임은 DB에서 최신 정보 조회
                "profileImageUrl", userService.getUserProfileImageUrl(appUser.getId()), // 프로필 이미지 URL도 DB에서 조회
                "role", appUser.getRole()
        ));
    }

    // 사용자 정보 수정 (예: 닉네임, 프로필 이미지 URL)
    @PutMapping("/me")
    public ResponseEntity<?> updateMyInfo(@AuthenticationPrincipal AppUser appUser, @RequestBody UserUpdateRequestDto updateRequest) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        try {
            userService.updateUserInfo(appUser.getId(), updateRequest.getNickname(), updateRequest.getProfileImageUrl());
            return ResponseEntity.ok(Map.of("message", "사용자 정보가 성공적으로 업데이트되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "정보 업데이트 중 오류 발생: " + e.getMessage()));
        }
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        // access_token 쿠키 제거
        Cookie accessTokenCookie = new Cookie("access_token", null);
        accessTokenCookie.setMaxAge(0); // 즉시 만료
        accessTokenCookie.setPath("/");
        accessTokenCookie.setHttpOnly(true);
        response.addCookie(accessTokenCookie);

        // refresh_token 쿠키 제거
        Cookie refreshTokenCookie = new Cookie("refresh_token", null);
        refreshTokenCookie.setMaxAge(0); // 즉시 만료
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setHttpOnly(true);
        response.addCookie(refreshTokenCookie);

        return ResponseEntity.ok(Map.of("message", "로그아웃되었습니다."));
    }

    // 회원 탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMyAccount(@AuthenticationPrincipal AppUser appUser, HttpServletResponse response) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        try {
            userService.deleteUser(appUser.getId());

            // access_token 쿠키 제거
            Cookie accessTokenCookie = new Cookie("access_token", null);
            accessTokenCookie.setMaxAge(0); // 즉시 만료
            accessTokenCookie.setPath("/");
            accessTokenCookie.setHttpOnly(true);
            response.addCookie(accessTokenCookie);

            // refresh_token 쿠키 제거
            Cookie refreshTokenCookie = new Cookie("refresh_token", null);
            refreshTokenCookie.setMaxAge(0); // 즉시 만료
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setHttpOnly(true);
            response.addCookie(refreshTokenCookie);

            return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 성공적으로 처리되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "회원 탈퇴 중 오류 발생: " + e.getMessage()));
        }
    }

    @PutMapping("/me/settings/notifications")
    public ResponseEntity<?> updateNotificationSettings(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody NotificationSettingsDto settingsDto) {
        User updatedUser = userService.updateNotificationSettings(appUser.getId(), settingsDto);
        // 업데이트된 전체 설정을 다시 DTO로 만들어 반환
        NotificationSettingsDto newSettings = new NotificationSettingsDto(
                updatedUser.isNotificationsEnabled(),
                updatedUser.isNotifyTodaySchedule(),
                updatedUser.isNotifyNextSchedule(),
                updatedUser.isNotifyRoutineProgress(),
                updatedUser.isNotifySupplies(),
                updatedUser.isNotifyUnexpectedEvent(),
                updatedUser.isNotifyAiFeature()
        );
        return ResponseEntity.ok(Map.of("message", "알림 설정이 업데이트되었습니다.", "settings", newSettings));
    }

    @GetMapping("/me/settings/notifications")
    public ResponseEntity<NotificationSettingsDto> getNotificationSettings(@AuthenticationPrincipal AppUser appUser) {
        NotificationSettingsDto settings = userService.getNotificationSettings(appUser.getId());
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<?> saveFcmToken(@AuthenticationPrincipal AppUser appUser, @RequestBody FcmTokenRequest fcmTokenRequest) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        if (fcmTokenRequest == null || fcmTokenRequest.getFcmToken() == null || fcmTokenRequest.getFcmToken().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "FCM 토큰이 제공되지 않았습니다."));
        }
        try {
            userService.saveUserFcmToken(appUser.getId(), fcmTokenRequest.getFcmToken());
            return ResponseEntity.ok(Map.of("message", "FCM 토큰이 성공적으로 저장되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "FCM 토큰 저장 중 오류 발생: " + e.getMessage()));
        }
    }

    @PostMapping("/app-fcm-token") // 앱 FCM 토큰 저장 엔드포인트
    public ResponseEntity<?> saveAppFcmToken(@AuthenticationPrincipal AppUser appUser, @RequestBody FcmTokenRequest fcmTokenRequest) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        if (fcmTokenRequest == null || fcmTokenRequest.getFcmToken() == null || fcmTokenRequest.getFcmToken().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "앱 FCM 토큰이 제공되지 않았습니다."));
        }
        try {
            // '앱' 토큰을 저장하는 새로운 서비스 메서드 호출♡
            userService.saveUserAppFcmToken(appUser.getId(), fcmTokenRequest.getFcmToken());
            return ResponseEntity.ok(Map.of("message", "앱 FCM 토큰이 성공적으로 저장되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "앱 FCM 토큰 저장 중 오류 발생: " + e.getMessage()));
        }
    }
}