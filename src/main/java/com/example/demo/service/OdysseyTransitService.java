package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import com.example.demo.dto.odsay.OdsaySearchStationResponse;
import com.example.demo.dto.odsay.OdsaySubwayStationInfoResponse;
import com.example.demo.dto.odsay.OdsayPointSearchResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono; // Mono 클래스

/**
 * 오디세이 API를 사용한 대중교통 시간 조회 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OdysseyTransitService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${odsay.api.key:}")
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

    /**
     * ODsay API로 역 이름 검색 (searchStation)
     * @param stationName 검색할 역 이름 (2자 이상)
     * @return 검색된 정류장/역 목록 Mono (stationClass 필터링 전)
     */
    /**
     * ODsay API로 역 이름 검색 (searchStation)
     * @param stationName 검색할 역 이름 (2자 이상)
     * @return 검색된 지하철역 목록 Mono (stationClass=2 필터링 적용)
     */
    public Mono<List<OdsaySearchStationResponse.StationInfo>> searchStationByName(String stationName) {
        // RestTemplate 호출을 Mono.fromCallable로 감싸 비동기처럼 반환
        return Mono.fromCallable(() -> {
            // API 키 값 확인 로그 (유지)
            if (odysseyApiKey == null || odysseyApiKey.isBlank()) {
                log.error("❌ [OdysseyTransitService] ODsay API Key가 로드되지 않았습니다! application.properties 파일을 확인하세요.");
                return Collections.<OdsaySearchStationResponse.StationInfo>emptyList();
            }

            try {
                // stationName과 apiKey 모두 인코딩
                String encodedApiKey = URLEncoder.encode(odysseyApiKey, StandardCharsets.UTF_8.toString());
                String encodedStationName = URLEncoder.encode(stationName, StandardCharsets.UTF_8.toString());

                // *** [수정] String.format에 apiKey(%s)와 해당 값(encodedApiKey) 다시 추가 ***
                String urlString = String.format(
                    "https://api.odsay.com/v1/api/searchStation?apiKey=%s&lang=0&stationName=%s&stationClass=2",
                    encodedApiKey, // *** API 키 값 전달 ***
                    encodedStationName
                );
                URI uri = URI.create(urlString);

                log.info("🔍 [OdysseyTransitService] 역 이름 검색: '{}'", stationName);
                log.debug("Request URL: {}", urlString); // 로그에서 apiKey 값 확인 가능

                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");

                ResponseEntity<String> response = restTemplate.exchange(
                        uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

                // ... (이하 응답 처리 및 에러 핸들링 로직은 이전과 동일) ...
                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("ODsay searchStation raw response for '{}': {}", stationName, responseBody);

                    JsonNode root = objectMapper.readTree(responseBody);
                     if (root.has("error")) {
                         String errorCode = root.path("error").get(0).path("code").asText();
                         String errorMessage = root.path("error").get(0).path("message").asText();
                         log.warn("⚠️ [OdysseyTransitService] ODsay API Error (searchStation for '{}'): code={}, message={}", stationName, errorCode, errorMessage);
                         if ("500".equals(errorCode) && errorMessage.contains("ApiKeyAuthFailed")) {
                            log.error("!!!!!!!!!!!!!!!!!!!! ODsay API Key 인증 실패! application.properties 또는 환경 변수를 확인하세요. 전달된 키: {}", odysseyApiKey);
                         }
                         return Collections.<OdsaySearchStationResponse.StationInfo>emptyList();
                     }

                    OdsaySearchStationResponse parsedResponse = objectMapper.readValue(
                        responseBody, OdsaySearchStationResponse.class);

                    if (parsedResponse != null && parsedResponse.getResult() != null
                        && parsedResponse.getResult().getStation() != null) {

                        List<OdsaySearchStationResponse.StationInfo> stations = parsedResponse.getResult().getStation();
                        // stationClass=2 필터링 (API 파라미터 + 응답 확인)
                        List<OdsaySearchStationResponse.StationInfo> subwayStations = stations.stream()
                            .filter(s -> s.getStationClass() != null && s.getStationClass() == 2)
                            .collect(Collectors.toList());

                        log.info("✅ [OdysseyTransitService] '{}' 검색 결과: {}개 역 발견 (필터링 후 {}개)",
                                 stationName, stations.size(), subwayStations.size());
                        return subwayStations;

                    } else {
                        log.warn("⚠️ [OdysseyTransitService] '{}' 검색 결과 구조가 비어있습니다.", stationName);
                        return Collections.<OdsaySearchStationResponse.StationInfo>emptyList();
                    }
                } else {
                    log.warn("⚠️ [OdysseyTransitService] API 응답 코드: {}", response.getStatusCode());
                    return Collections.<OdsaySearchStationResponse.StationInfo>emptyList();
                }
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                 log.error("❌ [OdysseyTransitService] 역 이름 검색 API 호출 실패 ('{}'): Status={}, Body={}", stationName, e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return Collections.<OdsaySearchStationResponse.StationInfo>emptyList();
            } catch (Exception e) {
                log.error("❌ [OdysseyTransitService] 역 이름 검색 처리 실패 ('{}'): {}", stationName, e.getMessage(), e);
                return Collections.<OdsaySearchStationResponse.StationInfo>emptyList();
            }
        });
    }


    /**
     * 특정 지하철역의 상세 정보 조회 (ODsay API: subwayStationInfo)
     * @param stationID 지하철역 코드 (int 타입 주의!)
     * @return 역 상세 정보 DTO Mono
     */
    public Mono<OdsaySubwayStationInfoResponse> getStationInfo(int stationID) {
        // RestTemplate 호출을 Mono.fromCallable로 감싸 비동기처럼 반환
        return Mono.fromCallable(() -> {
             try {
                String encodedApiKey = URLEncoder.encode(odysseyApiKey, StandardCharsets.UTF_8);
                String urlString = String.format(
                    "https://api.odsay.com/v1/api/subwayStationInfo?apiKey=%s&stationID=%d",
                    encodedApiKey, stationID
                );
                URI uri = URI.create(urlString);

                log.info("ℹ️ [OdysseyTransitService] 역 상세정보 조회: stationID={}", stationID);
                log.debug("Request URL: {}", urlString);

                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");

                ResponseEntity<String> response = restTemplate.exchange(
                        uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("ODsay subwayStationInfo response for {}: {}", stationID, responseBody);

                    // 에러 응답 우선 체크
                    JsonNode root = objectMapper.readTree(responseBody);
                     if (root.has("error")) {
                         log.warn("⚠️ [OdysseyTransitService] ODsay API Error (subwayStationInfo for {}): code={}, message={}", stationID, root.path("error").get(0).path("code").asText(), root.path("error").get(0).path("message").asText());
                         // 에러 시 빈 응답 객체 반환 대신 예외 발생 고려
                         throw new RuntimeException("ODsay API Error for subwayStationInfo: " + root.path("error").get(0).path("message").asText());
                     }

                    OdsaySubwayStationInfoResponse parsedResponse = objectMapper.readValue(
                        responseBody, OdsaySubwayStationInfoResponse.class);

                    if (parsedResponse != null && parsedResponse.getResult() != null) {
                         log.info("✅ [OdysseyTransitService] 역 {} 상세정보 조회 성공", stationID);
                         return parsedResponse;
                    } else {
                        log.warn("⚠️ [OdysseyTransitService] 역 {} 상세정보 결과 구조가 비어있습니다.", stationID);
                        throw new RuntimeException("역 상세정보 결과 구조가 비어있습니다: " + stationID);
                    }

                } else {
                    log.warn("⚠️ [OdysseyTransitService] API 응답 코드: {}", response.getStatusCode());
                    throw new RuntimeException("API 응답 코드 에러: " + response.getStatusCode());
                }
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                 log.error("❌ [OdysseyTransitService] 역 상세정보 조회 API 호출 실패 (stationID: {}): Status={}, Body={}", stationID, e.getStatusCode(), e.getResponseBodyAsString(), e);
                 throw new RuntimeException("API 호출 실패", e); // 예외 다시 던지기
            } catch (Exception e) {
                log.error("❌ [OdysseyTransitService] 역 상세정보 조회 처리 실패 (stationID: {}): {}",
                         stationID, e.getMessage(), e);
                 throw new RuntimeException("처리 실패", e); // 예외 다시 던지기
            }
        });
    }

    // --- 기존 메소드들 (findNearbyStations, getUniqueLaneNames using subwayTransitInfo) ---
    // 이 메소드들은 이제 주 로직에서 사용되지 않지만, 필요하면 남겨두거나 삭제할 수 있습니다.
    // getTransitTime은 @Deprecated 상태로 유지합니다.

    /**
     * @deprecated 이제 searchStation과 subwayStationInfo 조합을 사용합니다.
     */
    @Deprecated
    public Mono<List<OdsayPointSearchResponse.Station>> findNearbyStations(double longitude, double latitude, int radius) {
        // ... (이전 답변의 코드 유지) ...
         return Mono.fromCallable(() -> {
            try {
                String encodedApiKey = URLEncoder.encode(odysseyApiKey, StandardCharsets.UTF_8);
                String urlString = String.format(
                    "https://api.odsay.com/v1/api/pointSearch?apiKey=%s&x=%s&y=%s&radius=%d&stationClass=1",
                    encodedApiKey, longitude, latitude, radius
                );
                URI uri = URI.create(urlString);

                log.info("🔍 [OdysseyTransitService(Deprecated)] 근처 역/정류장 조회: lon={}, lat={}, radius={}m (stationClass=1 요청)",
                         longitude, latitude, radius);
                log.debug("Request URL: {}", urlString);

                HttpHeaders headers = new HttpHeaders();
                headers.set("Accept", "application/json");

                ResponseEntity<String> response = restTemplate.exchange(
                        uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

                if (response.getStatusCode() == HttpStatus.OK) {
                    String responseBody = response.getBody();
                    log.debug("ODsay pointSearch raw response: {}", responseBody);

                    JsonNode root = objectMapper.readTree(responseBody);
                     if (root.has("error")) {
                         log.warn("⚠️ [OdysseyTransitService(Deprecated)] ODsay API Error (pointSearch): code={}, message={}", root.path("error").get(0).path("code").asText(), root.path("error").get(0).path("message").asText());
                         return Collections.emptyList();
                     }

                    OdsayPointSearchResponse parsedResponse = objectMapper.readValue(
                        responseBody, OdsayPointSearchResponse.class);

                    if (parsedResponse != null && parsedResponse.getResult() != null
                        && parsedResponse.getResult().getStation() != null) {
                        List<OdsayPointSearchResponse.Station> allStations = parsedResponse.getResult().getStation();
                        log.info("✅ [OdysseyTransitService(Deprecated)] API 응답: 총 {}개의 역/정류장 발견", allStations.size());
                        List<OdsayPointSearchResponse.Station> subwayStations = allStations.stream()
                                .filter(station -> "1".equals(station.getStationClass()))
                                .collect(Collectors.toList());
                        if (subwayStations.isEmpty()) {
                             log.warn("⚠️ [OdysseyTransitService(Deprecated)] 필터링 결과: 근처에 지하철역(stationClass=1)이 없습니다.");
                        } else {
                            log.info("✅ [OdysseyTransitService(Deprecated)] 필터링 결과: {}개의 지하철역만 추출", subwayStations.size());
                        }
                        return subwayStations;
                    } else {
                        log.warn("⚠️ [OdysseyTransitService(Deprecated)] 결과 구조가 비어있습니다.");
                        return Collections.emptyList();
                    }
                } else {
                    log.warn("⚠️ [OdysseyTransitService(Deprecated)] API 응답 코드: {}", response.getStatusCode());
                    return Collections.emptyList();
                }
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                 log.error("❌ [OdysseyTransitService(Deprecated)] 지하철역 조회 API 호출 실패: Status={}, Body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                 return Collections.emptyList();
            } catch (Exception e) {
                log.error("❌ [OdysseyTransitService(Deprecated)] 지하철역 조회 처리 실패: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        });
    }

}
