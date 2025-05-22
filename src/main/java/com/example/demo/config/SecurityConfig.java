package com.example.demo.config;

import com.example.demo.jwt.JwtAuthenticationFilter;
import com.example.demo.jwt.JwtTokenProvider;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.OAuth2UserService;
import jakarta.servlet.http.Cookie;
import lombok.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // HttpMethod import 추가
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
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)) // JWT를 사용하므로 STATELESS도 고려 가능하나, OAuth2 로그인 세션 유지를 위해 IF_REQUIRED 유지
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 모든 OPTIONS 요청 허용
                        .requestMatchers("/", "/login/**", "/oauth2/**", "/user/profile/edit","/calendar-test","/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                        )
                        .successHandler((request, response, authentication) -> {
                            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                            OAuth2User oAuth2User = oauthToken.getPrincipal();
                            String email = oAuth2User.getAttribute("email");
                            String token = jwtTokenProvider.createToken(email);

                            Cookie cookie = new Cookie("access_token", token);
                            cookie.setPath("/");
                            cookie.setHttpOnly(true); // JavaScript에서 접근 불가
                            // cookie.setSecure(true); // HTTPS에서만 전송 (배포 시 활성화)
                            cookie.setMaxAge((int)(jwtTokenProvider.getExpirationHours() * 60 * 60));
                            response.addCookie(cookie);

                            Boolean isNewUser = oAuth2User.getAttribute("isNewUser");

                            if (Boolean.TRUE.equals(isNewUser)) {
                                response.sendRedirect("/user/profile/edit");
                            } else {
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