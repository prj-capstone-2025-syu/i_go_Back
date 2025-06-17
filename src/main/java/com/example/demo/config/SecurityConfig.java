package com.example.demo.config;

import com.example.demo.jwt.JwtAuthenticationFilter;
import com.example.demo.jwt.JwtTokenProvider;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.OAuth2UserService;
import jakarta.servlet.http.Cookie;
import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Arrays;

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

    //TODO : 나중에 EC2 옮길떄 바꿀것
    @Value("${frontend.url:https://www.igo.ai.kr}")
    //@Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(frontendUrl));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/greeting", // Greeting 페이지
                                "/login/**", // 로그인 관련 경로
                                "/oauth2/**", // OAuth2 관련 경로
                                "/api/auth/refresh-token", // Refresh Token 엔드포인트
                                "/error",     // 에러 페이지
                                // 프론트엔드 정적 리소스 및 Next.js 내부 경로 (필요에 따라 추가)
                                "/favicon.ico",
                                "/logo.png", // greeting 페이지에서 사용될 수 있는 이미지
                                "/_next/**", // Next.js 빌드 파일
                                "/static/**", // 기타 정적 파일
                                "/manifest.json",
                                "/robots.txt",
                                "/firebase-messaging-sw.js" // FCM 서비스 워커
                        ).permitAll()
                        .requestMatchers("/app/v1/**", "/app/test/**").denyAll() // 프론트엔드 특정 경로 접근 완전 차단
                        .requestMatchers("/api/**").authenticated() // API 경로는 인증 필요
                        .anyRequest().authenticated() // 그 외 모든 요청은 인증 필요 (루트 포함)
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                        )
                        .successHandler((request, response, authentication) -> {
                            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
                            OAuth2User oAuth2User = oauthToken.getPrincipal();
                            String email = oAuth2User.getAttribute("email");

                            String accessToken = jwtTokenProvider.createAccessToken(email);
                            String refreshToken = jwtTokenProvider.createRefreshToken(email);

                            // User 엔티티에 Refresh Token 저장
                            userRepository.findByEmail(email).ifPresent(user -> {
                                user.setAppRefreshToken(refreshToken);
                                user.setAppRefreshTokenExpiresAt(
                                        LocalDateTime.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirationMillis() / 1000)
                                );
                                userRepository.save(user);
                            });

                            Cookie accessTokenCookie = new Cookie("access_token", accessToken);
                            accessTokenCookie.setPath("/");
                            accessTokenCookie.setHttpOnly(false); // JavaScript에서 접근 가능하도록 설정 (필요에 따라 true로 변경)
                            accessTokenCookie.setMaxAge((int) (jwtTokenProvider.getAccessTokenExpirationHours() * 60 * 60)); // 초 단위
                            response.addCookie(accessTokenCookie);

                            Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken);
                            refreshTokenCookie.setPath("/"); // API 경로에만 적용하려면 "/api" 등으로 변경 가능
                            refreshTokenCookie.setHttpOnly(true);
                            refreshTokenCookie.setSecure(request.isSecure()); // HTTPS에서만 전송
                            refreshTokenCookie.setMaxAge((int) (jwtTokenProvider.getRefreshTokenExpirationMillis() / 1000)); // jwt.refresh 값을 초 단위로 설정
                            response.addCookie(refreshTokenCookie);

                            response.sendRedirect(frontendUrl); // 로그인 성공 시 프론트엔드 루트로 리다이렉트
                        })
                )
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(new CustomAuthenticationEntryPoint(frontendUrl + "/greeting"))
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenProvider, userRepository),
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}