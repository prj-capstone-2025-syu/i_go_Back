package com.example.demo.dto.chat;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Long userId;
} 