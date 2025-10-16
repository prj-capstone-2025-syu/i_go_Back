package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 오디세이 API를 사용한 대중교통 시간 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdysseyTransitService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${odyssey.api.key:}")
    private String odysseyApiKey;

    /**
     * 오디세이 API로 대중교통 소요 시간 조회
     * @param startX 출발지 경도
     * @param startY 출발지 위도
     * @param endX 도착지 경도
     * @param endY 도착지 위도
     * @return 대중교통 소요 시간 (분), 실패 시 null
     */
    public Integer getTransitTime(Double startX, Double startY, Double endX, Double endY) {
        try {
            // 오디세이 API 엔드포인트
            String url = "https://api.odsay.com/v1/api/searchPubTransPathT";

            // URL에 파라미터 추가
            String urlWithParams = String.format("%s?SX=%s&SY=%s&EX=%s&EY=%s&apiKey=%s",
                    url, startX, startY, endX, endY, odysseyApiKey);

            log.info("🚇 [OdysseyTransitService] 대중교통 시간 조회 시작 - 출발({}, {}) -> 도착({}, {})",
                    startX, startY, endX, endY);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");

            ResponseEntity<String> response = restTemplate.exchange(
                    urlWithParams, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());

                // 오디세이 API 응답 구조: result.path[0].info.totalTime (분 단위)
                JsonNode resultNode = root.path("result");

                if (resultNode.has("path") && !resultNode.path("path").isEmpty()) {
                    JsonNode firstPath = resultNode.path("path").get(0);
                    JsonNode info = firstPath.path("info");

                    if (info.has("totalTime")) {
                        int totalTimeMinutes = info.path("totalTime").asInt();
                        log.info("✅ [OdysseyTransitService] 대중교통 시간 조회 성공: {}분", totalTimeMinutes);
                        return totalTimeMinutes;
                    } else {
                        log.warn("⚠️ [OdysseyTransitService] totalTime 필드를 찾을 수 없습니다.");
                    }
                } else {
                    log.warn("⚠️ [OdysseyTransitService] 경로(path) 정보가 없거나 비어있습니다.");
                }
            } else {
                log.warn("⚠️ [OdysseyTransitService] API 응답 코드: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ [OdysseyTransitService] 대중교통 시간 조회 실패: {}", e.getMessage(), e);
        }

        return null;
    }
}
