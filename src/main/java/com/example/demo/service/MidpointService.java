package com.example.demo.service;

import com.example.demo.dto.midpoint.Coordinates; // Coordinates DTO import
import com.example.demo.dto.midpoint.GooglePlace; // GooglePlace DTO import
import com.example.demo.dto.midpoint.GooglePlacesResponse; // GooglePlacesResponse DTO import
import com.example.demo.exception.LocationNotFoundException; // LocationNotFoundException import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets; // StandardCharsets import 추가
import java.util.Collections; // Collections import 추가
import java.util.List;

/**
 * Google Places API를 사용하여 주변 장소를 검색하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidpointService {

    private final RestTemplate restTemplate;

    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;

    private static final String PLACES_API_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";

    /**
     * 주어진 좌표 근처에서 특정 타입의 장소를 Google Places API로 검색합니다.
     * @param coords 중심 좌표
     * @param placeType 검색할 장소 타입 (e.g., "subway_station", "restaurant")
     * @return GooglePlace 객체 목록
     * @throws LocationNotFoundException 장소를 찾지 못했을 경우
     */
    public List<GooglePlace> getNearbyPlaces(Coordinates coords, String placeType) {
        if (coords == null || placeType == null || placeType.isBlank()) {
            throw new IllegalArgumentException("좌표와 장소 타입은 필수입니다.");
        }

        log.info("🔍 Google Places: Searching for '{}' near ({}, {})",
                 placeType, coords.getLat(), coords.getLng());

        // 1. 거리순 검색 시도 (가장 정확)
        List<GooglePlace> places = searchNearbyPlacesRankedByDistance(coords, placeType);

        // 2. 거리순 결과 없으면 반경 1km 검색 시도 (Fallback 1)
        if (places.isEmpty()) {
            log.warn("Google Places: No results with rankby=distance. Trying radius search (1km)...");
            places = searchNearbyPlacesWithRadius(coords, placeType, 1000); // 반경 1km
        }

        // 3. 반경 1km 결과도 없으면 반경 2km 검색 시도 (Fallback 2)
        if (places.isEmpty()) {
            log.warn("Google Places: No results within 1km radius. Trying radius search (2km)...");
            places = searchNearbyPlacesWithRadius(coords, placeType, 2000); // 반경 2km
        }

        // 4. 최종 결과 확인 및 반환
        if (places.isEmpty()) {
            // 2km 반경에서도 못 찾으면 최종 실패 처리
            log.error("Google Places: No results found even after fallback 2km radius search for type '{}'.", placeType);
            throw new LocationNotFoundException(String.format("좌표 (%.6f, %.6f) 2km 반경 내에서 '%s' 타입의 장소를 찾을 수 없습니다.",
                                                coords.getLat(), coords.getLng(), placeType));
        }

        log.info("✅ Google Places: Found {} places.", places.size());
        return places;
    }

    /**
     * Google Places Nearby Search API 호출 (거리순 정렬)
     */
    private List<GooglePlace> searchNearbyPlacesRankedByDistance(Coordinates coords, String type) {
        URI uri = UriComponentsBuilder.fromHttpUrl(PLACES_API_URL)
                .queryParam("location", coords.getLat() + "," + coords.getLng())
                .queryParam("rankby", "distance") // 거리순
                .queryParam("type", type)
                .queryParam("key", googleMapsApiKey)
                .queryParam("language", "ko")
                .encode(StandardCharsets.UTF_8) // 인코딩 명시
                .build(true) // alreadyEncoded=true 방지 (Builder가 인코딩하도록)
                .toUri();

        log.debug("Google Places API request (rankby=distance, type={}): {}", type, uri);
        try {
            GooglePlacesResponse response = restTemplate.getForObject(uri, GooglePlacesResponse.class);
            if (response != null && "OK".equals(response.getStatus()) && response.getResults() != null) {
                return response.getResults();
            } else if (response != null && !"ZERO_RESULTS".equals(response.getStatus())) {
                // ZERO_RESULTS 외의 오류 상태 로깅
                log.warn("Google Places API (rankby=distance) returned status: {}", response.getStatus());
            }
            return Collections.emptyList(); // OK 아니거나 results 없으면 빈 리스트
        } catch (Exception e) {
             log.error("Error calling Google Places API (rankby=distance) for type {}: {}", type, e.getMessage());
             log.debug("Google Places API Exception details:", e); // 디버깅용 스택 트레이스
            return Collections.emptyList(); // 에러 시 빈 리스트
        }
    }

    /**
     * Google Places Nearby Search API 호출 (반경 지정) - Fallback용
     */
    private List<GooglePlace> searchNearbyPlacesWithRadius(Coordinates coords, String type, int radius) {
        URI uri = UriComponentsBuilder.fromHttpUrl(PLACES_API_URL)
                .queryParam("location", coords.getLat() + "," + coords.getLng())
                .queryParam("radius", radius)
                .queryParam("type", type)
                .queryParam("key", googleMapsApiKey)
                .queryParam("language", "ko")
                .encode(StandardCharsets.UTF_8)
                .build(true)
                .toUri();

         log.debug("Google Places API request (radius={}, type={}): {}", radius, type, uri);
        try {
            GooglePlacesResponse response = restTemplate.getForObject(uri, GooglePlacesResponse.class);
            if (response != null && "OK".equals(response.getStatus()) && response.getResults() != null) {
                return response.getResults();
            } else if (response != null && !"ZERO_RESULTS".equals(response.getStatus())) {
                log.warn("Google Places API (radius) returned status: {}", response.getStatus());
            }
            return Collections.emptyList();
        } catch (Exception e) {
             log.error("Error calling Google Places API (radius) for type {}: {}", type, e.getMessage());
             log.debug("Google Places API Exception details:", e);
            return Collections.emptyList();
        }
    }
}