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

        // Google Access Token 정보 추출
        String accessToken = userRequest.getAccessToken().getTokenValue();
        LocalDateTime expiresAt = userRequest.getAccessToken().getExpiresAt() != null
                ? LocalDateTime.ofInstant(userRequest.getAccessToken().getExpiresAt(), java.time.ZoneId.systemDefault()).plusHours(1) // 1시간 추가
                : null;

        // 애플리케이션 자체 Refresh Token 생성 (JwtTokenProvider를 통해 생성해야 하지만, 여기서는 우선 null로 처리하고 SecurityConfig에서 생성된 값을 받아와야 함)
        // 이 부분은 SecurityConfig의 successHandler에서 생성된 refreshToken을 User 엔티티에 저장하는 로직으로 대체되어야 합니다.
        // 따라서 OAuth2UserService에서는 직접 Refresh Token을 생성하지 않습니다.

        Optional<User> userOptional = userRepository.findByOauthId(oauthId);
        User user;
        boolean isNewUser = false;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            user.setNickname(name);
            user.setProfileImageUrl(picture);
            user.setLastLoginAt(LocalDateTime.now());

            // Google 토큰 정보 업데이트
            user.setGoogleAccessToken(accessToken);
            user.setGoogleTokenExpiresAt(expiresAt);
            // appRefreshToken 및 appRefreshTokenExpiresAt는 SecurityConfig의 successHandler에서 처리 후 저장됩니다.
            // 여기서는 User 엔티티가 이미 존재할 경우 해당 필드를 업데이트하지 않도록 합니다.
            // 새로운 사용자일 경우에만 SecurityConfig에서 생성된 토큰으로 설정됩니다.

            userRepository.save(user);
        } else {
            isNewUser = true;
            user = User.builder()
                    .email(email)
                    .nickname(generateUniqueNickname(name))
                    .oauthId(oauthId)
                    .profileImageUrl(picture)
                    .status(UserStatus.ACTIVE)
                    .registeredAt(LocalDateTime.now())
                    .lastLoginAt(LocalDateTime.now())
                    // Google 토큰 정보 저장
                    .googleAccessToken(accessToken)
                    .googleTokenExpiresAt(expiresAt)
                    // appRefreshToken 및 appRefreshTokenExpiresAt는 SecurityConfig의 successHandler에서 생성 후
                    // User 객체에 설정되어 userRepository.save(user)를 통해 저장됩니다.
                    .build();
            userRepository.save(user);
        }

        Map<String, Object> userAttributes = new HashMap<>(originalAttributes);
        userAttributes.put("isNewUser", isNewUser);

        return new DefaultOAuth2User(
                Collections.singleton(new org.springframework.security.core.authority.SimpleGrantedAuthority(user.getRole())),
                userAttributes,
                "email"
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