package com.example.demo.controller;

import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final UserService userService;

    @GetMapping("/check")
    public ResponseEntity<?> checkSession(@AuthenticationPrincipal AppUser appUser) {
        if (appUser == null) {
            return ResponseEntity.status(401).body(Map.of("message", "인증되지 않은 사용자입니다."));
        }
        return ResponseEntity.ok(Map.of(
            "id", appUser.getId(),
            "email", appUser.getEmail(),
            "nickname", userService.getUserNickname(appUser.getId()),
            "profileImageUrl", userService.getUserProfileImageUrl(appUser.getId()),
            "role", appUser.getRole()
        ));
    }
} 