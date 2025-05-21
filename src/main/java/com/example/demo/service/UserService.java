package com.example.demo.service;

import com.example.demo.entity.user.User;
import com.example.demo.entity.user.UserStatus;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

        // 실제 데이터 삭제 대신 상태 변경 (Soft Delete)
        user.setStatus(UserStatus.DELETED);
        // 필요에 따라 개인정보를 마스킹하거나 null 처리할 수 있습니다.
        // user.setEmail("deleted_user_" + user.getId() + "@example.com"); // 예시
        // user.setNickname("탈퇴한사용자");
        // user.setOauthId(null); // 재가입을 허용하려면 oauthId도 null 처리 또는 다른 값으로 변경
        userRepository.save(user);
    }
}