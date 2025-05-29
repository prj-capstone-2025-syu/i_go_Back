package com.example.demo.dto.Chat;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Long userId;
} 