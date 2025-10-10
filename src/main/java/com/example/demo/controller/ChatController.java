package com.example.demo.controller;

import com.example.demo.dto.chat.ChatRequest;
import com.example.demo.dto.chat.ChatResponse;
import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> processMessage(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody ChatRequest request) {

        // 로그인된 사용자 ID 설정 (null이면 설정)
        if (request.getUserId() == null) {
            request.setUserId(appUser.getId());
        }

        // 클라이언트에서 현재 시간을 보내지 않은 경우 서버 시간으로 설정
        if (request.getCurrentTime() == null) {
            request.setCurrentTime(LocalDateTime.now());
        }

        log.debug("Chat request - User: {}, Message: {}, CurrentTime: {}",
                  request.getUserId(), request.getMessage(), request.getCurrentTime());

        ChatResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }
}
