package com.example.demo.service;

import com.example.demo.dto.midpoint.*;
import com.example.demo.exception.LocationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MidpointService {

    private final RestTemplate restTemplate;
    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;
    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String PLACES_API_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";

    // 순수하게 수학적 중간 좌표 '만' 계산해서 반환하는 메서드
    public Coordinates calculateGeometricMidpoint(List<String> locations) {
        if (locations == null || locations.size() < 2) {
            throw new IllegalArgumentException("최소 2개 이상의 위치가 필요합니다.");
        }
        List<Coordinates> coordinatesList = new ArrayList<>();
        for (String location : locations) {
            coordinatesList.add(getCoordinatesForLocation(location));
        }
        return calculateMidpoint(coordinatesList);
    }

    // 이 메서드는 '선호도'를 인자로 받아서 동적으로 검색 타입을 결정함
    public List<GooglePlace> getNearbyPlaces(Coordinates coords, String preferences) {
        String searchType = mapPreferenceToApiType(preferences);
        log.info("User preference '{}' mapped to Google API type '{}'", preferences, searchType);

        List<GooglePlace> places = searchNearbyPlaces(coords, searchType, 1000); // 반경을 1km로 통일

        // 만약 결과가 없으면, 가장 기본적인 'point_of_interest'로 한번 더 검색
        if (places.isEmpty()) {
            log.warn("No results for type '{}', falling back to 'point_of_interest'", searchType);
            places = searchNearbyPlaces(coords, "point_of_interest", 1500);
        }

        if (places.isEmpty()) {
            log.warn("### GPT CROSS-VALIDATION ### No candidate places were found by Google Places API.");
            throw new LocationNotFoundException("계산된 중간지점 근처에서 '" + preferences + "'에 해당하는 장소를 찾을 수 없습니다.");
        } else {
            log.info("### GPT CROSS-VALIDATION ### Found {} candidate places for preference '{}':", places.size(), preferences);
            for (int i = 0; i < places.size(); i++) {
                GooglePlace place = places.get(i);
                log.info("  -> Candidate [{}]: Name='{}', Address='{}', Rating={}",
                        i + 1, place.getName(), place.getVicinity(), place.getRating());
            }
            log.info("### END OF CANDIDATE LIST ###");
        }
        return places;
    }

    // 사용자 입력을 Google API 타입으로 변환하는 헬퍼 메서드
    private String mapPreferenceToApiType(String preference) {
        if (preference == null) return "point_of_interest";
        String lowerPref = preference.toLowerCase();

        if (lowerPref.contains("지하철") || lowerPref.contains("역")) {
            return "subway_station";
        } else if (lowerPref.contains("식당") || lowerPref.contains("맛집") || lowerPref.contains("밥")) {
            return "restaurant";
        } else if (lowerPref.contains("카페") || lowerPref.contains("커피")) {
            return "cafe";
        } else if (lowerPref.contains("공원")) {
            return "park";
        } else if (lowerPref.contains("술집") || lowerPref.contains("호프")) {
            return "bar";
        }
        // 매칭되는게 없으면 일반적인 검색
        return "point_of_interest";
    }

    private List<GooglePlace> searchNearbyPlaces(Coordinates coords, String type, int radius) {
        URI uri = UriComponentsBuilder.fromHttpUrl(PLACES_API_URL).queryParam("location", coords.getLat() + "," + coords.getLng()).queryParam("radius", radius).queryParam("type", type).queryParam("key", googleMapsApiKey).queryParam("language", "ko").build(true).toUri();
        log.info("Searching for nearby places ({}) with URI: {}", type, uri);
        GooglePlacesResponse response = restTemplate.getForObject(uri, GooglePlacesResponse.class);
        if (response != null && "OK".equals(response.getStatus()) && response.getResults() != null) {
            return response.getResults();
        }
        return new ArrayList<>();
    }
    public Coordinates getCoordinatesForLocation(String locationName) {
        try {
            String encodedAddress = UriComponentsBuilder.newInstance().queryParam("address", locationName.trim()).build().encode(StandardCharsets.UTF_8).getQuery().split("=")[1];
            String urlString = GEOCODING_API_URL + "?address=" + encodedAddress + "&language=ko" + "&region=kr" + "&key=" + googleMapsApiKey;
            log.info("Requesting Geocoding URL: {}", urlString);
            URI uri = URI.create(urlString);
            GoogleGeocodingResponse response = restTemplate.getForObject(uri, GoogleGeocodingResponse.class);
            if (response == null || !"OK".equals(response.getStatus()) || response.getResults() == null || response.getResults().isEmpty()) {
                String status = (response != null) ? response.getStatus() : "NULL_RESPONSE";
                log.warn("Geocoding failed for '{}' with status: {}", locationName, status);
                if ("ZERO_RESULTS".equals(status)) throw new LocationNotFoundException("'" + locationName + "'에 대한 위치를 찾을 수 없습니다.");
                throw new LocationNotFoundException("Geocoding API 오류: " + status);
            }
            GoogleGeocodingResponse.Location location = response.getResults().get(0).getGeometry().getLocation();
            log.info("✅ Found coordinates for '{}': lat={}, lng={}", locationName, location.getLat(), location.getLng());
            return new Coordinates(location.getLat(), location.getLng());
        } catch (Exception e) {
            if (e instanceof LocationNotFoundException) throw e;
            log.error("Unexpected error for '{}': {}", locationName, e.getMessage(), e);
            throw new LocationNotFoundException("'" + locationName + "' 위치 조회 중 알 수 없는 오류가 발생했습니다.", e);
        }
    }
    private Coordinates calculateMidpoint(List<Coordinates> coordinatesList) {
        double totalLat = 0.0, totalLng = 0.0;
        for (Coordinates coords : coordinatesList) {
            totalLat += coords.getLat();
            totalLng += coords.getLng();
        }
        return new Coordinates(totalLat / coordinatesList.size(), totalLng / coordinatesList.size());
    }
}