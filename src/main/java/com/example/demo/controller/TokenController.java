package com.example.demo.controller;

import com.example.demo.dto.token.TokenResponseDto;
import com.example.demo.service.TokenService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;
    private final com.example.demo.jwt.JwtTokenProvider jwtTokenProvider; // JwtTokenProvider 주입

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            TokenResponseDto tokenResponseDto = tokenService.refreshAccessToken(request);

            // 새로운 Access Token을 쿠키에 저장
            Cookie accessTokenCookie = new Cookie("access_token", tokenResponseDto.getAccessToken());
            accessTokenCookie.setPath("/");
            accessTokenCookie.setHttpOnly(false); // JavaScript에서 접근 가능하도록 설정
            // Access Token의 만료 시간 설정 (JwtTokenProvider에서 가져옴)
            accessTokenCookie.setMaxAge((int) (jwtTokenProvider.getAccessTokenExpirationHours() * 60 * 60)); // 초 단위
            response.addCookie(accessTokenCookie);

            return ResponseEntity.ok(tokenResponseDto);
        } catch (JwtException e) {
            // Refresh Token이 유효하지 않거나 문제가 있을 경우, 클라이언트가 재로그인하도록 유도
            // 기존 쿠키 삭제
            Cookie accessTokenCookie = new Cookie("access_token", null);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge(0);
            response.addCookie(accessTokenCookie);

            Cookie refreshTokenCookie = new Cookie("refresh_token", null);
            refreshTokenCookie.setPath("/");
            refreshTokenCookie.setMaxAge(0);
            response.addCookie(refreshTokenCookie);
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }
}

