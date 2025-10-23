package com.example.demo.dto.odsay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * ODsay pointSearch API 응답 DTO
 * (좌표 기반 주변 정류장/역 검색 결과)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OdsayPointSearchResponse {

    private Result result;
    private Error error; // 에러 응답 필드

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Integer count; // 결과 개수
        private List<Station> station; // 정류장/역 목록
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Station {
        private String stationName;      // 역/정류장 이름
        private String stationID;        // ODsay 역/정류장 ID (String 타입)
        private String laneName;         // (지하철역의 경우) 대표 노선명
        private Double x;                // 경도 (longitude)
        private Double y;                // 위도 (latitude)
        private Integer nonstopStation;  // 무정차 통과 여부 (0: 정차, 1: 무정차)
        private String stationClass;     // 정류장 종류 (1: 지하철, 2: 버스 등) - 필터링에 중요
        private String arsID;            // (버스정류장의 경우) 정류소 번호
        private String ebid;             // (버스정류장의 경우) ID
    }

    // 에러 응답 구조 내부 클래스
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Error {
        private List<ErrorDetail> error; // API 응답은 배열 형태
    }

    // 에러 상세 정보 내부 클래스
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {
        private String code;
        private String message;
    }
}