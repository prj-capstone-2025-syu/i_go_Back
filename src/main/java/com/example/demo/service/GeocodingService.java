package com.example.demo.service;

import com.example.demo.dto.midpoint.Coordinates; // *** DTO import ***
import com.example.demo.dto.midpoint.GoogleGeocodingResponse; // Geocoding DTO
import com.example.demo.exception.LocationNotFoundException; // Exception import
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper; // 주입 확인

    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;

    private static final List<String> SKIP_KEYWORDS = Arrays.asList( /* ... 키워드 ... */
        "비대면", "화상회의", "화상", "온라인", "줌", "zoom", "미팅", "meeting",
        "구글미트", "google meet", "teams", "webex", "skype"
    );

    /**
     * 주소를 좌표로 변환 (비대면 키워드 체크 포함)
     * @param address 주소 문자열
     * @return com.example.demo.dto.midpoint.Coordinates 객체, 실패 시 null
     */
    public Coordinates getCoordinates(String address) { // *** 반환 타입 수정 ***
        if (address == null || address.trim().isEmpty()) {
            log.warn("주소 입력값이 비어있습니다.");
            return null;
        }
        if (shouldSkipGeocoding(address.trim())) {
            log.info("비대면/온라인 키워드 감지. 주소 검색 건너뜀: {}", address.trim());
            return null;
        }
        return getCoordinatesInternal(address, 0);
    }

    private boolean shouldSkipGeocoding(String address) {
        String lowerAddress = address.toLowerCase();
        return SKIP_KEYWORDS.stream()
                .anyMatch(keyword -> lowerAddress.contains(keyword.toLowerCase()));
    }

    /**
     * 주소 -> 좌표 변환 내부 로직 (재귀 호출 포함)
     * @param address 검색할 주소
     * @param retryCount 재시도 횟수
     * @return com.example.demo.dto.midpoint.Coordinates 객체, 실패 시 null
     */
    private Coordinates getCoordinatesInternal(String address, int retryCount) { // 반환 타입은 DTO Coordinates
        if (retryCount >= 3) {
            log.warn("주소 검색 재시도 횟수 초과: {}", address);
            return null;
        }
        if (countOccurrences(address, "주변") >= 2) {
            log.warn("주소에 '주변'이 과도하게 포함되어 검색 중단: {}", address);
            return null;
        }

        try {
            // [수정] .encode(StandardCharsets.UTF_8) 호출 제거
            URI uri = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                    .queryParam("address", address.trim()) // Builder가 UTF-8로 자동 인코딩
                    .queryParam("key", googleMapsApiKey)
                    .queryParam("language", "ko")
                    .queryParam("region", "kr")
                    .build(false) // build(false)로 설정하여 템플릿 변수가 없음을 명시 (선택적)
                    .toUri(); // toUri() 전에 별도 encode() 호출 없음

            log.debug("Geocoding request for '{}' -> URL: {}", address, uri);

            GoogleGeocodingResponse response = restTemplate.getForObject(uri, GoogleGeocodingResponse.class);

            // ... (이하 응답 처리 및 재귀 호출 로직은 동일) ...
            if (response != null && "OK".equals(response.getStatus())
                    && response.getResults() != null && !response.getResults().isEmpty()) {
                GoogleGeocodingResponse.Location location = response.getResults().get(0).getGeometry().getLocation();
                Coordinates resultCoords = new Coordinates(location.getLat(), location.getLng());
                log.info("✅ Found coordinates for '{}': lat={}, lng={}", address, resultCoords.getLat(), resultCoords.getLng());
                return resultCoords;
            } else if (response != null && "ZERO_RESULTS".equals(response.getStatus())) {
                String[] alternatives = generateAlternativeSearchTerms(address);
                for (String alternative : alternatives) {
                    if (alternative.equals(address) || alternative.trim().isEmpty()) continue;
                    log.info("주소 검색 실패. 대체 검색어로 재시도 ({}/3): {}", retryCount + 1, alternative);
                    Coordinates coords = getCoordinatesInternal(alternative, retryCount + 1);
                    if (coords != null) return coords;
                }
                log.warn("대체 검색어로도 '{}' 위치를 찾지 못했습니다.", address);
                return null;
            } else {
                 String status = (response != null) ? response.getStatus() : "NULL_RESPONSE";
                 log.warn("Geocoding failed for '{}' with status: {}", address, status);
                 return null;
            }

        } catch (Exception e) {
            log.error("Error getting coordinates for address '{}': {}", address, e.getMessage());
            log.debug("Geocoding Exception Stacktrace:", e);
            return null;
        }
    }

    // 대체 검색어 생성 로직
    private String[] generateAlternativeSearchTerms(String address) {
        if (address.endsWith("역") && address.length() > 1) {
             String baseName = address.substring(0, address.length() - 1);
             return new String[]{ baseName, address + " 지하철역" };
        }
        String trimmedAddress = address.replaceAll("\\s+", "");
         if (!trimmedAddress.equals(address)) {
             return new String[]{ trimmedAddress };
         }
         return new String[]{};
    }

    // 문자열 내 특정 단어 출현 횟수 계산 유틸리티
    private int countOccurrences(String text, String keyword) {
         // ... (이전과 동일) ...
         if (text == null || keyword == null || text.isEmpty() || keyword.isEmpty()) return 0;
         int count = 0, index = 0;
         while ((index = text.indexOf(keyword, index)) != -1) {
             count++; index += keyword.length();
         }
         return count;
    }

}