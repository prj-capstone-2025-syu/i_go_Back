package com.example.demo.service;

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
    private final ObjectMapper objectMapper;

    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;

    // 주소 검색을 건너뛰어야 하는 키워드 목록
    private static final List<String> SKIP_KEYWORDS = Arrays.asList(
        "비대면", "화상회의", "화상", "온라인", "줌", "zoom", "미팅", "meeting",
        "구글미트", "google meet", "teams", "webex", "skype"
    );

    public Coordinates getCoordinates(String address) {
        // 비대면/온라인 관련 키워드가 포함된 경우 주소 검색 건너뛰기
        if (shouldSkipGeocoding(address)) {
            log.info("비대면/온라인 키워드 감지. 주소 검색 건너뜀: {}", address);
            return null;
        }

        return getCoordinatesInternal(address, 0);
    }

    private boolean shouldSkipGeocoding(String address) {
        String lowerAddress = address.toLowerCase();
        return SKIP_KEYWORDS.stream()
                .anyMatch(keyword -> lowerAddress.contains(keyword.toLowerCase()));
    }

    private Coordinates getCoordinatesInternal(String address, int retryCount) {
        // 재귀 깊이 제한 (무한 반복 방지)
        if (retryCount >= 3) {
            log.warn("주소 검색 재시도 횟수 초과: {}", address);
            return null;
        }

        // "주변"이 2번 이상 반복되면 중단
        if (address.split("주변").length > 3) {
            log.warn("주소에 '주변'이 과도하게 포함되어 검색 중단: {}", address);
            return null;
        }

        try {
            // UriComponentsBuilder를 사용하여 자동 인코딩 (이중 인코딩 방지)
            URI uri = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                    .queryParam("address", address.trim())
                    .queryParam("key", googleMapsApiKey)
                    .queryParam("language", "ko")
                    .queryParam("region", "kr")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            log.debug("Geocoding request for '{}' -> URL: {}", address, uri);
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("status") && "OK".equals(root.get("status").asText())) {
                JsonNode location = root.get("results")
                        .get(0)
                        .get("geometry")
                        .get("location");

                return new Coordinates(
                        location.get("lat").asDouble(),
                        location.get("lng").asDouble()
                );
            } else if (root.has("status") && "ZERO_RESULTS".equals(root.get("status").asText())) {
                // 주소를 찾을 수 없는 경우 대체 검색어로 재시도
                String[] alternatives = generateAlternativeSearchTerms(address);
                for (String alternative : alternatives) {
                    if (alternative.equals(address)) {
                        continue; // 동일한 주소는 건너뛰기
                    }
                    log.info("주소 검색 실패. 대체 검색어로 재시도 ({}/3): {}", retryCount + 1, alternative);
                    Coordinates coords = getCoordinatesInternal(alternative, retryCount + 1);
                    if (coords != null) {
                        return coords;
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Error getting coordinates for address '{}': {}", address, e.getMessage());
            return null;
        }
    }

    private String[] generateAlternativeSearchTerms(String address) {
        // 지하철역의 경우
        if (address.endsWith("역")) {
            return new String[]{
                address.substring(0, address.length() - 1), // "역" 제거
                address + " 지하철역",
                address + " 전철역"
            };
        }

        // 일반 주소의 경우
        if (address.contains("시") || address.contains("구") || address.contains("동")) {
            return new String[]{
                address.replaceAll("\\s+", ""), // 공백 제거
                address + " 일대"
            };
        }

        // "주변"이 이미 포함되어 있으면 추가하지 않음
        if (address.contains("주변")) {
            return new String[]{
                address.replaceAll("\\s+", "") // 공백 제거만 수행
            };
        }

        return new String[]{
            address.replaceAll("\\s+", "") // 공백 제거
        };
    }

    public static class Coordinates {
        private final double lat;
        private final double lng;

        public Coordinates(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }
    }
}