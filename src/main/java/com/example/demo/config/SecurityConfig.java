package com.example.demo.config;

import com.example.demo.jwt.JwtAuthenticationFilter;
import com.example.demo.jwt.JwtTokenProvider;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.OAuth2UserService;
import jakarta.servlet.http.Cookie;
import lombok.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final OAuth2UserService oAuth2UserService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login/**", "/oauth2/**", "/user/profile/edit","/calendar-test","/error").permitAll() // 프로필 수정 페이지 경로도 permitAll에 추가
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                        )
                        .successHandler((request, response, authentication) -> {
                            // OAuth2 로그인 성공 처리
                            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                            OAuth2User oAuth2User = oauthToken.getPrincipal();

                            // 구글에서 제공하는 이메일로 JWT 토큰 생성
                            String email = oAuth2User.getAttribute("email");
                            String token = jwtTokenProvider.createToken(email);

                            // JWT 토큰을 쿠키에 저장
                            Cookie cookie = new Cookie("access_token", token);
                            cookie.setPath("/");
                            cookie.setHttpOnly(true);
                            cookie.setMaxAge(3600); // 1시간
                            response.addCookie(cookie);

                            // 신규 사용자 여부 확인
                            Boolean isNewUser = oAuth2User.getAttribute("isNewUser");

                            if (Boolean.TRUE.equals(isNewUser)) {
                                // 신규 가입자인 경우 프로필 수정 페이지로 리다이렉트
                                response.sendRedirect("/user/profile/edit"); // 실제 프로필 수정 페이지 경로로 변경 필요!!!
                            } else {
                                // 기존 가입자는 메인 페이지로 리다이렉트
                                response.sendRedirect("/");
                            }
                        })
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}