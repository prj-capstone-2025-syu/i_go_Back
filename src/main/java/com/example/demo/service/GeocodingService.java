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

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;

    public Coordinates getCoordinates(String address) {
        try {
            // diff (-) 수동 인코딩 라인 삭제
            // String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.toString());

            // diff (+) UriComponentsBuilder에 원본 주소를 직접 전달하고, toUri()로 최종 URI 생성
            URI uri = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                    .queryParam("address", address) // 원본 주소 사용
                    .queryParam("key", googleMapsApiKey)
                    .queryParam("language", "ko")
                    .queryParam("region", "kr")
                    .build(true) // 인코딩 활성화
                    .toUri();

            log.debug("Geocoding request for '{}' -> URL: {}", address, uri);
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);

            String status = root.path("status").asText();

            if ("OK".equals(status)) {
                JsonNode location = root.path("results").path(0).path("geometry").path("location");
                return new Coordinates(location.path("lat").asDouble(), location.path("lng").asDouble());
            }

            // diff (+) 재귀 호출을 안전한 for 반복문으로 변경
            if ("ZERO_RESULTS".equals(status)) {
                log.warn("주소 '{}' 검색 실패. 대체 검색어를 시도합니다.", address);
                for (String alternative : generateAlternativeSearchTerms(address)) {
                    log.info("대체 검색어 시도: {}", alternative);
                    // 재귀가 아닌, 별도 private 메서드로 호출하여 중복 코드 방지 및 스택오버플로우 방지
                    Coordinates coords = attemptGeocoding(alternative);
                    if (coords != null) {
                        return coords;
                    }
                }
            }

            log.error("Geocoding 최종 실패. 주소: '{}', 상태: {}", address, status);
            return null;

        } catch (Exception e) {
            log.error("Error getting coordinates for address '{}': {}", address, e.getMessage());
            return null;
        }
    }

    // diff (+) 실제 API 호출 로직을 별도 메서드로 분리
    private Coordinates attemptGeocoding(String address) {
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                .queryParam("address", address)
                .queryParam("key", googleMapsApiKey)
                .queryParam("language", "ko")
                .queryParam("region", "kr")
                .build(true).toUri();

            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);

            if ("OK".equals(root.path("status").asText())) {
                JsonNode location = root.path("results").path(0).path("geometry").path("location");
                return new Coordinates(location.path("lat").asDouble(), location.path("lng").asDouble());
            }
        } catch (Exception e) {
            log.warn("대체 검색어 '{}' 처리 중 오류: {}", address, e.getMessage());
        }
        return null;
    }

    private String[] generateAlternativeSearchTerms(String address) {
        if (address.endsWith("역")) return new String[]{ address.substring(0, address.length() - 1), address + " 지하철역" };
        if (address.contains("시") || address.contains("구") || address.contains("동")) return new String[]{ address.replaceAll("\\s+", ""), address + " 일대" };
        return new String[]{ address + " 주변", address.replaceAll("\\s+", "") };
    }

    public static class Coordinates {
        private final double lat;
        private final double lng;
        public Coordinates(double lat, double lng) { this.lat = lat; this.lng = lng; }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }
}