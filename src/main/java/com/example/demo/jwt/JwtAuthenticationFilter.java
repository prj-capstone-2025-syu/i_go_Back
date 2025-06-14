package com.example.demo.jwt;

import com.example.demo.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import java.util.List;
import java.util.Arrays;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;
import java.util.ArrayList;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    // 인증이 필요하지 않은 URL 패턴 목록 (SecurityConfig와 일치하게 유지)
    private final List<RequestMatcher> permitAllMatchers = Arrays.asList(
            new AntPathRequestMatcher("/greeting"),
            new AntPathRequestMatcher("/login/**"),
            new AntPathRequestMatcher("/oauth2/**"),
            new AntPathRequestMatcher("/api/auth/refresh-token"),
            new AntPathRequestMatcher("/error"),
            new AntPathRequestMatcher("/favicon.ico"),
            new AntPathRequestMatcher("/logo.png"),
            new AntPathRequestMatcher("/_next/**"),
            new AntPathRequestMatcher("/static/**"),
            new AntPathRequestMatcher("/manifest.json"),
            new AntPathRequestMatcher("/robots.txt"),
            new AntPathRequestMatcher("/firebase-messaging-sw.js"),
            new AntPathRequestMatcher("/**", "OPTIONS") // OPTIONS 메소드는 허용
    );

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        return Arrays.stream(request.getCookies())
                .filter(cookie -> "access_token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isPermitAllUrl(HttpServletRequest request) {
        return permitAllMatchers.stream()
                .anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {
        if (isPermitAllUrl(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);

        // API 경로에 대해서는 토큰이 없거나 유효하지 않으면 직접 401 응답
        if (request.getRequestURI().startsWith("/api/")) {
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\": \"API 요청에 유효한 인증 토큰이 필요합니다.\"}");
                return;
            }
        }

        // 토큰이 존재하고 유효한 경우에만 인증 시도
        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserId(token);
            java.util.Optional<com.example.demo.entity.user.User> userOptional = userRepository.findByEmail(userId);

            if (userOptional.isPresent()) {
                com.example.demo.entity.user.User user = userOptional.get();
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else {
                // 토큰은 유효하나 사용자가 DB에 없는 경우, 인증 컨텍스트를 비움
                SecurityContextHolder.clearContext();
            }
        } else {
            // 토큰이 없거나 유효하지 않은 경우 (그리고 API 요청이 아니어서 위에서 반환되지 않은 경우),
            // 인증 컨텍스트를 비움
            SecurityContextHolder.clearContext();
        }

        // 모든 경우에 필터 체인의 다음 단계로 진행
        // 인증이 필요한데 SecurityContext에 인증 정보가 없으면
        // ExceptionTranslationFilter가 CustomAuthenticationEntryPoint를 호출함
        filterChain.doFilter(request, response);
    }
}