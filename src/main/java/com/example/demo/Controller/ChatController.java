package com.example.demo.Controller;

import com.example.demo.dto.Chat.ChatRequest;
import com.example.demo.dto.Chat.ChatResponse;
import com.example.demo.entity.entityInterface.AppUser;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ChatResponse> processMessage(
            @AuthenticationPrincipal AppUser appUser,
            @RequestBody ChatRequest request) {
        request.setUserId(appUser.getId());
        ChatResponse response = chatService.processMessage(request);
        return ResponseEntity.ok(response);
    }
} 