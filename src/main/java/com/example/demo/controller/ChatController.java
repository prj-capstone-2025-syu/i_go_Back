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

        log.info("==================== 챗봇 요청 시작 ====================");
        log.info("✅ [1단계] ChatController - 요청 수신");
        log.info("    - 원본 요청: userId={}, message='{}', currentTime={}",
            request.getUserId(), request.getMessage(), request.getCurrentTime());
        log.info("    - 인증된 사용자: {}", appUser != null ? appUser.getId() : "null");

        // 로그인된 사용자 ID 설정 (null이면 설정)
        if (request.getUserId() == null) {
            request.setUserId(appUser.getId());
            log.info("    - userId가 null이어서 인증된 사용자 ID로 설정: {}", appUser.getId());
        }

        // 클라이언트에서 현재 시간을 보내지 않은 경우 서버 시간으로 설정
        if (request.getCurrentTime() == null) {
            request.setCurrentTime(LocalDateTime.now());
            log.info("    - currentTime이 null이어서 서버 시간으로 설정: {}", request.getCurrentTime());
        }

        log.info("✅ [2단계] ChatService.processMessage() 호출");
        log.info("    - 최종 파라미터: userId={}, message='{}', currentTime={}",
            request.getUserId(), request.getMessage(), request.getCurrentTime());

        ChatResponse response = chatService.processMessage(request);

        log.info("✅ [최종단계] ChatController - 응답 반환");
        log.info("    - 응답 메시지: '{}'", response.getMessage());
        log.info("    - Intent: {}, Action: {}, Success: {}",
            response.getIntent(), response.getAction(), response.isSuccess());
        if (response.getData() != null && response.getData() instanceof java.util.List) {
            log.info("    - 데이터 개수: {}", ((java.util.List<?>) response.getData()).size());
        } else {
            log.info("    - 데이터: {}", response.getData() != null ? "포함됨" : "없음");
        }
        log.info("======================================================");

        return ResponseEntity.ok(response);
    }
}
