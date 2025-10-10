package com.example.demo.dto.midpoint;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Request DTO for smart midpoint calculation with conversational AI
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartMidpointRequest {

    /**
     * User's message for conversational midpoint finding
     * Examples: "3명이 만나요", "강남역, 홍대, 신림역", "중간 장소 찾아줘"
     */
    private String message;

    /**
     * Optional: Meeting purpose for better AI recommendations
     */
    private String purpose;

    /**
     * Optional: Preferences for the meeting place
     * Examples: "카페", "식당", "공원", "지하철역 근처"
     */
    private String preferences;
}
