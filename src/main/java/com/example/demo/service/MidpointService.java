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

    public MidpointResponse findMidpoint(List<String> locations) {
        try {
            MidpointResponse basicMidpoint = calculateBasicMidpoint(locations);
            if (!basicMidpoint.isSuccess()) return basicMidpoint;

            Coordinates midpointCoords = basicMidpoint.getMidpointCoordinates();
            log.info("Calculated geometric midpoint: {}", midpointCoords);

            // 목록을 가져와서 그 중 최고를 고르는 로직
            List<GooglePlace> nearbyPlaces = getNearbyPlaces(midpointCoords);
            GooglePlace bestPlace = nearbyPlaces.stream()
                .max(Comparator.comparing(GooglePlace::getRating))
                .orElse(nearbyPlaces.get(0));

            Coordinates finalCoordinates = new Coordinates(
                    bestPlace.getGeometry().getLocation().getLat(),
                    bestPlace.getGeometry().getLocation().getLng()
            );
            String finalAddress = bestPlace.getName() + " (" + bestPlace.getVicinity() + ")";
            log.info("Corrected midpoint to a real place: '{}' at {}", finalAddress, finalCoordinates);

            return MidpointResponse.builder()
                    .midpointCoordinates(finalCoordinates)
                    .midpointAddress(finalAddress)
                    .success(true)
                    .message("만나기 좋은 중간지점을 찾았습니다.")
                    .build();
        } catch (LocationNotFoundException e) {
            log.error("Location not found: {}", e.getMessage());
            return MidpointResponse.builder().success(false).message(e.getMessage()).build();
        } catch (Exception e) {
            log.error("Error calculating midpoint: {}", e.getMessage(), e);
            return MidpointResponse.builder().success(false).message("중간위치 계산 중 오류가 발생했습니다.").build();
        }
    }

    // [신규] 장소 목록 전체를 반환하는 public 메서드
    public List<GooglePlace> getNearbyPlaces(Coordinates coords) {
        List<GooglePlace> places = searchNearbyPlaces(coords, "subway_station", 500);
        if (places.isEmpty()) {
            log.info("No subway stations found, searching for cafes...");
            places = searchNearbyPlaces(coords, "cafe", 500);
        }
        if (places.isEmpty()) {
            log.warn("No cafes found, broadening search to any point of interest...");
            places = searchNearbyPlaces(coords, "point_of_interest", 1000);
        }

        if (places.isEmpty()) {
            log.warn("### GPT CROSS-VALIDATION ### No candidate places were found by Google Places API.");
            throw new LocationNotFoundException("계산된 중간지점 근처에서 만날 만한 장소를 찾을 수 없습니다.");
        } else {
            log.info("### GPT CROSS-VALIDATION ### Found {} candidate places. These will be used for recommendations:", places.size());
            for (int i = 0; i < places.size(); i++) {
                GooglePlace place = places.get(i);
                log.info("  -> Candidate [{}]: Name='{}', Address='{}', Rating={}",
                        i + 1, place.getName(), place.getVicinity(), place.getRating());
            }
            log.info("### END OF CANDIDATE LIST ###");
        }
        return places;
    }

    private GooglePlace findBestNearbyPlace(Coordinates coords) {
        List<GooglePlace> places = getNearbyPlaces(coords);
        return places.stream()
                .max(Comparator.comparing(GooglePlace::getRating))
                .orElse(places.get(0));
    }

    private List<GooglePlace> searchNearbyPlaces(Coordinates coords, String type, int radius) {
        URI uri = UriComponentsBuilder.fromHttpUrl(PLACES_API_URL)
                .queryParam("location", coords.getLat() + "," + coords.getLng())
                .queryParam("radius", radius)
                .queryParam("type", type)
                .queryParam("key", googleMapsApiKey)
                .queryParam("language", "ko")
                .build(true)
                .toUri();
        log.info("Searching for nearby places ({}) with URI: {}", type, uri);
        GooglePlacesResponse response = restTemplate.getForObject(uri, GooglePlacesResponse.class);
        if (response != null && "OK".equals(response.getStatus()) && response.getResults() != null) {
            return response.getResults();
        }
        return new ArrayList<>();
    }
    private MidpointResponse calculateBasicMidpoint(List<String> locations) {
        if (locations == null || locations.size() < 2) {
            throw new IllegalArgumentException("최소 2개 이상의 위치가 필요합니다.");
        }
        List<Coordinates> coordinatesList = new ArrayList<>();
        for (String location : locations) {
            coordinatesList.add(getCoordinatesForLocation(location));
        }
        Coordinates midpointCoords = calculateMidpoint(coordinatesList);
        String midpointAddress = getAddressForCoordinates(midpointCoords);
        return MidpointResponse.builder()
                .midpointCoordinates(midpointCoords)
                .midpointAddress(midpointAddress)
                .success(true)
                .build();
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
                if ("ZERO_RESULTS".equals(status)) {
                    throw new LocationNotFoundException("'" + locationName + "'에 대한 위치를 찾을 수 없습니다. 더 자세한 주소나 장소명을 입력해주세요.");
                }
                throw new LocationNotFoundException("Geocoding API 오류: " + status + " for location: " + locationName);
            }
            GoogleGeocodingResponse.Location location = response.getResults().get(0).getGeometry().getLocation();
            log.info("✅ Found coordinates for '{}': lat={}, lng={}", locationName, location.getLat(), location.getLng());
            return new Coordinates(location.getLat(), location.getLng());
        } catch (HttpClientErrorException e) {
            log.error("API Client Error for '{}': {} - {}", locationName, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new LocationNotFoundException("API 요청 오류: '" + locationName + "'. API 키가 유효한지 확인해주세요.", e);
        } catch (ResourceAccessException e) {
            log.error("Network Error for '{}': {}", locationName, e.getMessage(), e);
            throw new LocationNotFoundException("API 서버 연결 오류: '" + locationName + "'. 네트워크 상태를 확인해주세요.", e);
        } catch (Exception e) {
            if (e instanceof LocationNotFoundException) throw e;
            log.error("Unexpected error for '{}': {}", locationName, e.getMessage(), e);
            throw new LocationNotFoundException("'" + locationName + "' 위치 조회 중 알 수 없는 오류가 발생했습니다.", e);
        }
    }
    private String getAddressForCoordinates(Coordinates coordinates) {
         try {
            String urlString = UriComponentsBuilder.fromHttpUrl(GEOCODING_API_URL).queryParam("latlng", coordinates.getLat() + "," + coordinates.getLng()).queryParam("language", "ko").queryParam("key", googleMapsApiKey).toUriString();
            URI uri = URI.create(urlString);
            log.info("Requesting Reverse Geocoding URL: {}", uri);
            GoogleGeocodingResponse response = restTemplate.getForObject(uri, GoogleGeocodingResponse.class);
            if (response == null || !"OK".equals(response.getStatus()) || response.getResults() == null || response.getResults().isEmpty()) {
                throw new LocationNotFoundException("Reverse geocoding 실패: " + (response != null ? response.getStatus() : "null response"));
            }
            return response.getResults().get(0).getFormatted_address();
        } catch (Exception e) {
            log.warn("주소 조회 실패, 좌표만 반환: {}", e.getMessage());
            return String.format("위도: %.6f, 경도: %.6f", coordinates.getLat(), coordinates.getLng());
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