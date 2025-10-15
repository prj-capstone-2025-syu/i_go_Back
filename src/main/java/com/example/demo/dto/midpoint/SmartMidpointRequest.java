package com.example.demo.dto.midpoint;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Request DTO for smart midpoint calculation with conversational AI
 * All information is collected sequentially through the message field and stored in server session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartMidpointRequest {

    /**
     * User's message for conversational midpoint finding
     * Examples: "3명이 만나요", "강남역, 홍대, 신림역", "커피 마시려고요", "조용한 카페"
     */
    private String message;
}
