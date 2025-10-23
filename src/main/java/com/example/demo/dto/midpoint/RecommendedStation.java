package com.example.demo.dto.midpoint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

/**
 * 최종 추천될 지하철역 정보 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedStation {
    private String stationName;       // 역 이름 (Google Places 기준)
    private double longitude;         // 경도 (Google Places 기준)
    private double latitude;          // 위도 (Google Places 기준)
    private Set<String> uniqueLanes;  // 해당 역을 지나는 고유 노선 목록 (ODsay 기준)
    private int laneCount;            // 고유 노선 개수 (정렬용)
}