package com.example.demo.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import java.io.IOException;

public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final String redirectUrl;

    public CustomAuthenticationEntryPoint(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        String acceptHeader = request.getHeader("Accept");
        String requestedWithHeader = request.getHeader("X-Requested-With");

        // API 요청 fetch 판단될 경우
        if ((acceptHeader != null && acceptHeader.contains("application/json")) || "XMLHttpRequest".equals(requestedWithHeader)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Authentication required to access this resource.\"}");
        } else {
            // 일반 웹 페이지 요청일 경우 지정된 URL로 리다이렉트
            response.sendRedirect(redirectUrl);
        }
    }
}