package com.example.demo.dto.Chat;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class ChatResponse {
    private String message;
    private String intent;
    private String action;
    private Object data;
    private boolean success;
} 