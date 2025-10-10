package com.example.demo.dto.chat;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChatRequest {
    private String message;
    private Long userId;
    private LocalDateTime currentTime; // 사용자의 현재 시간
}
