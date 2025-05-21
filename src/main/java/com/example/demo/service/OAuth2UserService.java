package com.example.demo.service;

import com.example.demo.entity.user.User;
import com.example.demo.entity.user.UserStatus;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> originalAttributes = oAuth2User.getAttributes();

        String oauthProviderId = userRequest.getClientRegistration().getRegistrationId(); // "google"
        String oauthId = String.valueOf(originalAttributes.get("sub")); // Google의 경우 'sub'가 고유 ID
        String email = (String) originalAttributes.get("email");
        String name = (String) originalAttributes.get("name");
        String picture = (String) originalAttributes.get("picture");

        Optional<User> userOptional = userRepository.findByOauthId(oauthId);
        User user;
        boolean isNewUser = false;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            user.setNickname(name); // 이름이 변경되었을 수 있음.(닉네임 재설정)
            user.setProfileImageUrl(picture);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        } else {
            // 새 사용자 등록
            isNewUser = true; // 신규 사용자 플래그 설정
            user = User.builder()
                    .email(email)
                    .nickname(generateUniqueNickname(name)) // 닉네임 중복 방지
                    .oauthId(oauthId)
                    .profileImageUrl(picture)
                    .status(UserStatus.ACTIVE)
                    .registeredAt(LocalDateTime.now())
                    .lastLoginAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
        }

        // attributes에 isNewUser 플래그 추가
        Map<String, Object> userAttributes = new HashMap<>(originalAttributes);
        userAttributes.put("isNewUser", isNewUser);

        // userAttributes.put("userId", user.getId());

        return new DefaultOAuth2User(
                Collections.singleton(new org.springframework.security.core.authority.SimpleGrantedAuthority(user.getRole())),
                userAttributes, // 수정된 attributes 사용
                "email" // Principal Name으로 사용할 속성 키
        );
    }

    private String generateUniqueNickname(String baseNickname) {
        String nickname = baseNickname;
        int count = 1;
        while (userRepository.existsByNickname(nickname)) {
            nickname = baseNickname + count++;
        }
        return nickname;
    }
}