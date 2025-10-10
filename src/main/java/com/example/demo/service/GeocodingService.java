package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
            String encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.toString());
            String url = UriComponentsBuilder.fromHttpUrl("https://maps.googleapis.com/maps/api/geocode/json")
                    .queryParam("address", encodedAddress)
                    .queryParam("key", googleMapsApiKey)
                    .queryParam("language", "ko")
                    .queryParam("region", "kr")
                    .build()
                    .toString();

            log.debug("Geocoding request for '{}' -> URL: {}", address, url);
            String response = restTemplate.getForObject(url, String.class);
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
                    log.info("주소 검색 실패. 대체 검색어로 재시도: {}", alternative);
                    Coordinates coords = getCoordinates(alternative);
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
                address + " 일대",
                address + " 중심"
            };
        }

        return new String[]{
            address + " 주변",
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