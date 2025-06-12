package com.example.demo.dto.transport;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportTimeRequest {
    private Double startX;
    private Double startY;
    private Double endX;
    private Double endY;
    private boolean isRemoteEvent; // 비대면 일정 여부 추가
}