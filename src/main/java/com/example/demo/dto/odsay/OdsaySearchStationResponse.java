package com.example.demo.dto.odsay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OdsaySearchStationResponse {

    private Result result;
    private Error error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Integer totalCount; // 검색 결과 총 개수
        // private TotalCityList totalCityList; // 필요 시 추가
        private List<StationInfo> station; // 정류장/역 목록
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StationInfo {
        private Integer stationClass; // 정류장 종류 (2: 지하철역)
        private String stationName;   // 정류장/역 이름
        private Integer stationID;    // ODsay 정류장/역 ID (int 타입 주의)
        private Double x;             // 경도
        private Double y;             // 위도
        private String arsID;         // 버스 정류장 번호 (참고용)
        // localStationID, type, laneName 등 지하철역 정보는 이 API 응답에 없을 수 있음
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
}