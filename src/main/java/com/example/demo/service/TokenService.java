package com.example.demo.service;

import com.example.demo.dto.token.TokenResponseDto;
import com.example.demo.entity.user.User;
import com.example.demo.jwt.JwtTokenProvider;
import com.example.demo.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Transactional
    public TokenResponseDto refreshAccessToken(HttpServletRequest request) {
        String refreshTokenFromCookie = extractRefreshToken(request);

        if (refreshTokenFromCookie == null) {
            throw new JwtException("Refresh token not found in cookie.");
        }

        if (!jwtTokenProvider.validateToken(refreshTokenFromCookie)) {
            throw new JwtException("Invalid refresh token.");
        }

        String userId = jwtTokenProvider.getUserId(refreshTokenFromCookie);
        User user = userRepository.findByEmail(userId)
                .orElseThrow(() -> new JwtException("User not found with refresh token."));

        if (!refreshTokenFromCookie.equals(user.getAppRefreshToken()) || user.getAppRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            // DB에 저장된 리프레시 토큰과 다르거나, 만료된 경우
            // 보안을 위해 DB의 리프레시 토큰을 무효화 (null 또는 빈 값으로 업데이트)
            user.setAppRefreshToken(null);
            user.setAppRefreshTokenExpiresAt(null);
            userRepository.save(user);
            throw new JwtException("Refresh token is compromised or expired. Please login again.");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId);
        return new TokenResponseDto(newAccessToken);
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "refresh_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

