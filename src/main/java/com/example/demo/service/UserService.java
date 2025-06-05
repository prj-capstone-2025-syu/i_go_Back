package com.example.demo.service;

import com.example.demo.entity.user.User;
import com.example.demo.entity.user.UserStatus;
import com.example.demo.repository.*;
import com.example.demo.dto.NotificationSettingsDto;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final RoutineRepository routineRepository;
    private final RoutineItemRepository routineItemRepository;
    private final NotificationRepository notificationRepository;
    private final OAuthRevokeService oAuthRevokeService;

    @Transactional(readOnly = true)
    public String getUserNickname(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
        return user.getNickname();
    }

    @Transactional(readOnly = true)
    public String getUserProfileImageUrl(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
        return user.getProfileImageUrl();
    }

    @Transactional
    public void updateUserInfo(Long userId, String newNickname, String newProfileImageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        if (newNickname != null && !newNickname.isEmpty()) {
            // 닉네임 중복 검사 (자기 자신 제외)
            if (!user.getNickname().equals(newNickname) && userRepository.existsByNickname(newNickname)) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            user.setNickname(newNickname);
        }

        if (newProfileImageUrl != null) { // 프로필 이미지 URL은 비어있을 수도 있으므로 null 체크만
            user.setProfileImageUrl(newProfileImageUrl);
        }

        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 구글 OAuth 토큰 취소 (연결 해제)
        if (user.getGoogleAccessToken() != null && !user.getGoogleAccessToken().isEmpty()) {
            oAuthRevokeService.revokeGoogleToken(user.getGoogleAccessToken());
        }

        // 사용자 관련 데이터 삭제
        // 1. 사용자의 모든 알림 삭제
        notificationRepository.deleteAllByUserId(userId);

        // 2. 사용자의 루틴 항목 삭제
        routineItemRepository.deleteAllByUserId(userId);

        // 3. 사용자의 루틴 삭제
        routineRepository.deleteAllByUserId(userId);

        // 4. 사용자의 일정 삭제
        scheduleRepository.deleteAllByUserId(userId);

        // 5. 사용자 정보 마스킹 (소프트 삭제)
        user.setStatus(UserStatus.DELETED);

        // 개인정보 마스킹 처리
        // UUID를 사용하여 고유한 값으로 이메일 마스킹 (not null 제약조건 위반 방지)
        String uniqueDeletedEmail = "deleted_" + UUID.randomUUID().toString().substring(0, 8) + "_" + userId;
        user.setEmail(uniqueDeletedEmail);
        user.setNickname("탈퇴한사용자");

        // 프로필 이미지 URL이 null이 될 수 없는 경우 빈 문자열로 설정
        user.setProfileImageUrl("");

        // OAuth 관련 정보 초기화 - oauthId는 nullable=false 속성 때문에 삭제하지 않고 고유값으로 변경
        String randomOauthId = "deleted_" + UUID.randomUUID().toString();
        user.setOauthId(randomOauthId);

        user.setGoogleAccessToken(null);
        user.setGoogleRefreshToken(null);
        user.setGoogleTokenExpiresAt(null);

        // FCM 토큰 제거하여 푸시 알림 수신 중단
        user.setFcmToken(null);

        // 모든 알림 설정 비활성화
        user.setNotificationsEnabled(false);
        user.setNotifyTodaySchedule(false);
        user.setNotifyNextSchedule(false);
        user.setNotifyRoutineProgress(false);
        user.setNotifySupplies(false);
        user.setNotifyUnexpectedEvent(false);
        user.setNotifyAiFeature(false);

        userRepository.save(user);
    }

    public User updateNotificationSettings(Long userId, NotificationSettingsDto settingsDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));

        // 각 설정 값을 DTO로부터 업데이트 (null이 아닐 경우에만)
        if (settingsDto.getNotificationsEnabled() != null) {
            user.setNotificationsEnabled(settingsDto.getNotificationsEnabled());
        }
        if (settingsDto.getNotifyTodaySchedule() != null) {
            user.setNotifyTodaySchedule(settingsDto.getNotifyTodaySchedule());
        }
        if (settingsDto.getNotifyNextSchedule() != null) {
            user.setNotifyNextSchedule(settingsDto.getNotifyNextSchedule());
        }
        if (settingsDto.getNotifyRoutineProgress() != null) {
            user.setNotifyRoutineProgress(settingsDto.getNotifyRoutineProgress());
        }
        if (settingsDto.getNotifySupplies() != null) {
            user.setNotifySupplies(settingsDto.getNotifySupplies());
        }
        if (settingsDto.getNotifyUnexpectedEvent() != null) {
            user.setNotifyUnexpectedEvent(settingsDto.getNotifyUnexpectedEvent());
        }
        if (settingsDto.getNotifyAiFeature() != null) {
            user.setNotifyAiFeature(settingsDto.getNotifyAiFeature());
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public NotificationSettingsDto getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. ID: " + userId));
        return new NotificationSettingsDto(
                user.isNotificationsEnabled(),
                user.isNotifyTodaySchedule(),
                user.isNotifyNextSchedule(),
                user.isNotifyRoutineProgress(),
                user.isNotifySupplies(),
                user.isNotifyUnexpectedEvent(),
                user.isNotifyAiFeature()
        );
    }

    // FCM 토큰 저장 메서드
    public void saveUserFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }
}
