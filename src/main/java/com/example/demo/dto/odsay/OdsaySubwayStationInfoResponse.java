package com.example.demo.dto.odsay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.HashSet; // HashSet import 추가
import java.util.List;
import java.util.Set; // Set import 추가

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OdsaySubwayStationInfoResponse {

    private Result result;
    private Error error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String stationName;
        private Integer stationID;
        private Integer type;
        private String laneName; // 역 자체 노선명
        private String laneCity;
        private Double x;
        private Double y;
        private ExOBJ exOBJ; // 환승 노선 목록 포함 객체
        // 기타 필드 생략
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExOBJ {
        private List<StationDetail> station;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StationDetail {
        private String stationName;
        private Integer stationID;
        private Integer type;
        private String laneName; // 환승 노선명
        private String laneCity;
    }

    // --- Error Handling ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {
        private List<ErrorDetail> error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {
        private String code;
        private String message;
    }

    /**
     * 응답 객체에서 고유한 노선명 Set을 추출하는 헬퍼 메소드
     * @return 고유 노선명 Set (null 이거나 비어있을 수 있음)
     */
    public Set<String> collectUniqueLaneNames() {
        Set<String> uniqueLanes = new HashSet<>();
        if (this.result != null) {
            // 1. 역 자체의 대표 노선 추가
            if (this.result.getLaneName() != null && !this.result.getLaneName().trim().isEmpty()) {
                uniqueLanes.add(this.result.getLaneName().trim());
            }
            // 2. 환승 노선 목록(exOBJ) 순회하며 추가
            if (this.result.getExOBJ() != null && this.result.getExOBJ().getStation() != null) {
                for (StationDetail transferStation : this.result.getExOBJ().getStation()) {
                    if (transferStation.getLaneName() != null && !transferStation.getLaneName().trim().isEmpty()) {
                        uniqueLanes.add(transferStation.getLaneName().trim());
                    }
                }
            }
        }
        // 결과 Set 반환 (비어 있을 수 있음)
        return uniqueLanes;
    }
}