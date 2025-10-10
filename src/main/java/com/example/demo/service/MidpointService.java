package com.example.demo.service;

import com.example.demo.dto.midpoint.Coordinates;
import com.example.demo.dto.midpoint.GoogleGeocodingResponse;
import com.example.demo.dto.midpoint.MidpointResponse;
import com.example.demo.exception.LocationNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Service class for calculating geographical midpoints using Google Geocoding API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidpointService {

    private final RestTemplate restTemplate;

    @Value("${google.maps.api.key}")
    private String googleMapsApiKey;

    private static final String GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json";

    /**
     * Finds the geographical midpoint of multiple locations
     *
     * @param locations List of location names to find midpoint for
     * @return MidpointResponse containing midpoint coordinates and address
     */
    public MidpointResponse findMidpoint(List<String> locations) {
        try {
            if (locations == null || locations.isEmpty()) {
                return MidpointResponse.builder()
                        .success(false)
                        .message("위치 목록이 비어있습니다.")
                        .build();
            }

            if (locations.size() < 2) {
                return MidpointResponse.builder()
                        .success(false)
                        .message("최소 2개 이상의 위치가 필요합니다.")
                        .build();
            }

            log.info("Finding midpoint for locations: {}", locations);

            // 각 위치의 좌표를 가져오기
            List<Coordinates> coordinatesList = new ArrayList<>();
            for (String location : locations) {
                Coordinates coords = getCoordinatesForLocation(location);
                coordinatesList.add(coords);
            }

            // 중간점 계산
            Coordinates midpointCoords = calculateMidpoint(coordinatesList);

            // 중간점의 주소 가져오기
            String midpointAddress = getAddressForCoordinates(midpointCoords);

            log.info("Calculated midpoint: {} at address: {}", midpointCoords, midpointAddress);

            return MidpointResponse.builder()
                    .midpointCoordinates(midpointCoords)
                    .midpointAddress(midpointAddress)
                    .success(true)
                    .message("중간위치가 성공적으로 계산되었습니다.")
                    .build();

        } catch (LocationNotFoundException e) {
            log.error("Location not found: {}", e.getMessage());
            return MidpointResponse.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error calculating midpoint: {}", e.getMessage(), e);
            return MidpointResponse.builder()
                    .success(false)
                    .message("중간위치 계산 중 오류가 발생했습니다: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Gets coordinates for a given location name using Google Geocoding API
     *
     * @param locationName The name of the location to geocode
     * @return Coordinates object containing latitude and longitude
     * @throws LocationNotFoundException if location cannot be found
     */
    private Coordinates getCoordinatesForLocation(String locationName) {
        try {
            // 한글 주소 처리
            String encodedAddress = UriComponentsBuilder.fromPath("")
                .queryParam("address", locationName.trim())
                .build()
                .getQueryParams()
                .getFirst("address");

            String url = GEOCODING_API_URL +
                "?address=" + encodedAddress +
                "&key=" + googleMapsApiKey +
                "&language=ko" +
                "&region=kr";

            log.info("Geocoding request for '{}' -> URL: {}", locationName, url);

            GoogleGeocodingResponse response = restTemplate.getForObject(url, GoogleGeocodingResponse.class);

            if (response == null) {
                throw new LocationNotFoundException("Google Geocoding API 응답이 null입니다.");
            }

            log.info("Geocoding response status: {} for location: '{}'", response.getStatus(), locationName);

            if (!"OK".equals(response.getStatus())) {
                if ("ZERO_RESULTS".equals(response.getStatus())) {
                    // 첫 번째 시도 실패 시 간단한 대체 시도
                    return retryWithSimpleAlternatives(locationName);
                } else if ("OVER_QUERY_LIMIT".equals(response.getStatus())) {
                    throw new LocationNotFoundException("Google Maps API 할당량 초과");
                } else if ("REQUEST_DENIED".equals(response.getStatus())) {
                    throw new LocationNotFoundException("Google Maps API 키 오류 또는 권한 거부");
                } else {
                    throw new LocationNotFoundException("Geocoding 실패: " + response.getStatus() + " for location: " + locationName);
                }
            }

            if (response.getResults() == null || response.getResults().isEmpty()) {
                throw new LocationNotFoundException("위치 결과가 없습니다: " + locationName);
            }

            GoogleGeocodingResponse.Result result = response.getResults().get(0);
            GoogleGeocodingResponse.Location location = result.getGeometry().getLocation();

            log.info("✅ Found coordinates for '{}': lat={}, lng={}", locationName, location.getLat(), location.getLng());
            return new Coordinates(location.getLat(), location.getLng());

        } catch (LocationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new LocationNotFoundException("위치 조회 중 오류 발생: " + locationName, e);
        }
    }

    /**
     * 간단한 대체 지명으로 재시도 (한글 그대로 유지)
     */
    private Coordinates retryWithSimpleAlternatives(String originalName) {
        List<String> alternatives = new ArrayList<>();
        String base = originalName.trim();

        // 한글 지명 그대로 사용하는 간단한 대체안들
        if (!base.contains("역") && !base.contains("구") && !base.contains("동")) {
            alternatives.add(base + "역");        // 예: 서울 -> 서울역
            alternatives.add(base + "구");        // 예: 강남 -> 강남구
            alternatives.add(base + "동");        // 예: 역삼 -> 역삼동
        }

        if (base.endsWith("역")) {
            String stationName = base.substring(0, base.length() - 1);
            alternatives.add(stationName);        // 예: 서울역 -> 서울
            alternatives.add(stationName + "구"); // 예: 강남역 -> 강남구
        }

        for (String alternative : alternatives) {
            try {
                String encodedAddress = UriComponentsBuilder.fromPath("")
                    .queryParam("address", alternative)
                    .build()
                    .getQueryParams()
                    .getFirst("address");

                String url = GEOCODING_API_URL +
                    "?address=" + encodedAddress +
                    "&key=" + googleMapsApiKey +
                    "&language=ko" +
                    "&region=kr";

                log.info("Retry with alternative '{}' for '{}'", alternative, originalName);

                GoogleGeocodingResponse response = restTemplate.getForObject(url, GoogleGeocodingResponse.class);

                if (response != null && "OK".equals(response.getStatus()) &&
                    response.getResults() != null && !response.getResults().isEmpty()) {

                    GoogleGeocodingResponse.Result result = response.getResults().get(0);
                    GoogleGeocodingResponse.Location location = result.getGeometry().getLocation();

                    log.info("✅ Success with alternative '{}' for '{}'", alternative, originalName);
                    return new Coordinates(location.getLat(), location.getLng());
                }
            } catch (Exception e) {
                log.debug("Alternative '{}' failed: {}", alternative, e.getMessage());
            }
        }

        throw new LocationNotFoundException("모든 대체 지명으로도 위치를 찾을 수 없습니다: " + originalName);
    }

    /**
     * Gets address for given coordinates using Google Reverse Geocoding API
     *
     * @param coordinates The coordinates to reverse geocode
     * @return Formatted address string
     * @throws LocationNotFoundException if address cannot be found
     */
    private String getAddressForCoordinates(Coordinates coordinates) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL)
                    .queryParam("latlng", coordinates.getLat() + "," + coordinates.getLng())
                    .queryParam("key", googleMapsApiKey)
                    .queryParam("language", "ko")
                    .toUriString();

            log.debug("Reverse geocoding request URL: {}", url);

            GoogleGeocodingResponse response = restTemplate.getForObject(url, GoogleGeocodingResponse.class);

            if (response == null) {
                throw new LocationNotFoundException("Google Reverse Geocoding API 응답이 null입니다.");
            }

            if (!"OK".equals(response.getStatus())) {
                throw new LocationNotFoundException("Reverse geocoding 실패: " + response.getStatus());
            }

            if (response.getResults() == null || response.getResults().isEmpty()) {
                throw new LocationNotFoundException("주소 결과가 없습니다.");
            }

            return response.getResults().get(0).getFormatted_address();

        } catch (Exception e) {
            if (e instanceof LocationNotFoundException) {
                throw e;
            }
            log.warn("주소 조회 실패, 좌표만 반환: {}", e.getMessage());
            return String.format("위도: %.6f, 경도: %.6f", coordinates.getLat(), coordinates.getLng());
        }
    }

    /**
     * Calculates the geographical midpoint of multiple coordinates
     *
     * @param coordinatesList List of coordinates to calculate midpoint for
     * @return Coordinates object representing the midpoint
     */
    private Coordinates calculateMidpoint(List<Coordinates> coordinatesList) {
        double totalLat = 0.0;
        double totalLng = 0.0;

        for (Coordinates coords : coordinatesList) {
            totalLat += coords.getLat();
            totalLng += coords.getLng();
        }

        double midpointLat = totalLat / coordinatesList.size();
        double midpointLng = totalLng / coordinatesList.size();

        return new Coordinates(midpointLat, midpointLng);
    }
}
