package com.example.demo.dto.midpoint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 중간지점 계산 및 추천 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MidpointResponse {
    private Coordinates midpointCoordinates; // 계산된 지리적 중간 지점 (선택적)
    private String midpointAddress;       // 대표 추천 역 주소 또는 이름 (선택적)
    private boolean success;              // 요청 성공 여부
    private String message;               // 사용자에게 보여줄 메시지 (진행 상황 또는 최종 결과)
    private List<RecommendedStation> recommendedStations; // 추천된 지하철역 목록
}