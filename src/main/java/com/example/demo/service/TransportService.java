package com.example.demo.service;

import com.example.demo.dto.transport.TransportTimeRequest;
import com.example.demo.dto.transport.TransportTimeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransportService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tmap.appkey}")
    private String tmapAppKey;

    @Value("${tmap.transit.appkey}")
    private String tmapTransitAppKey;

    // 대중교통 API 일일 호출 수 제한을 추적하기 위한 카운터
    private static int transitApiCallCounter = 0;
    private static final int TRANSIT_API_DAILY_LIMIT = 10;

    // 마지막 카운터 리셋 시간
    private static long lastCounterResetTime = System.currentTimeMillis();

    // 캐시 구현 - 동일한 출발지/도착지에 대한 결과를 저장
    private static final ConcurrentMap<String, CachedTransportResult> transportTimeCache = new ConcurrentHashMap<>();

    // 캐시 결과의 유효 시간 (1시간)
    private static final long CACHE_VALIDITY_PERIOD = 60 * 60 * 1000;

    // 동일한 캐시 키에 대한 모든 API 호출을 동기화하기 위한 락 맵
    private static final ConcurrentMap<String, ReentrantLock> apiCallLocks = new ConcurrentHashMap<>();

    /**
     * 모든 교통수단의 이동시간을 계산
     */
    public TransportTimeResponse calculateAllTransportTimes(TransportTimeRequest request) {
        // 입력값 검증
        if (request.getStartX() == null || request.getStartY() == null ||
                request.getEndX() == null || request.getEndY() == null) {
            log.error("좌표값 누락: 모든 좌표(startX, startY, endX, endY)가 필요합니다");
            return TransportTimeResponse.builder().build();
        }

        // 비대면 일정인 경우 API 호출을 하지 않고 null 반환 (프론트엔드에서 처리함)
        if (request.isRemoteEvent()) {
            log.info("비대면 일정은 이동시간 계산이 필요 없습니다.");
            return TransportTimeResponse.builder().build();
        }

        // 캐시 키 생성
        String cacheKey = String.format("%f_%f_%f_%f",
                request.getStartX(), request.getStartY(),
                request.getEndX(), request.getEndY());

        // 🔒 캐시 키별로 락 획득하여 중복 호출 완전 방지
        ReentrantLock lock = apiCallLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

        try {
            lock.lock();
            log.info("🔒 전체 API 호출 락 획득: {} (스레드: {})", cacheKey, Thread.currentThread().getName());

            // 락 획득 후 캐시 재확인 (다른 스레드가 이미 계산했을 수 있음)
            CachedTransportResult cachedResult = transportTimeCache.get(cacheKey);
            if (cachedResult != null && !isCacheExpired(cachedResult)) {
                log.info("🎯 락 획득 후 캐시에서 모든 교통 시간 정보 반환: {}", cacheKey);
                return TransportTimeResponse.builder()
                        .walking(cachedResult.getWalking())
                        .driving(cachedResult.getDriving())
                        .transit(cachedResult.getTransit())
                        .build();
            }

            log.info("🚀 실제 API 호출 시작 - 캐시키: {} (스레드: {})", cacheKey, Thread.currentThread().getName());

            // 교통수단별 소요시간 계산 (락 보호 하에서)
            Integer walkingTime = calculateWalkingTimeInternal(request);
            Integer drivingTime = calculateDrivingTimeInternal(request);
            Integer transitTime = calculateTransitTimeInternal(request);

            // 결과 캐싱
            CachedTransportResult result = new CachedTransportResult(
                    walkingTime, drivingTime, transitTime, System.currentTimeMillis()
            );
            transportTimeCache.put(cacheKey, result);

            log.info("✅ 모든 API 호출 완료 및 캐시 저장: {} (도보:{}분, 자차:{}분, 대중교통:{}분)",
                    cacheKey, walkingTime, drivingTime, transitTime);

            return TransportTimeResponse.builder()
                    .walking(walkingTime)
                    .driving(drivingTime)
                    .transit(transitTime)
                    .build();

        } finally {
            lock.unlock();
            log.info("🔓 전체 API 호출 락 해제: {} (스레드: {})", cacheKey, Thread.currentThread().getName());

            // 락 맵에서 제거 (메모리 누수 방지)
            if (!lock.hasQueuedThreads()) {
                apiCallLocks.remove(cacheKey, lock);
            }
        }
    }

    /**
     * 대중교통 이동시간만 별도 계산
     */
    public Integer calculateTransitTimeOnly(TransportTimeRequest request) {
        // 캐시 키 생성
        String cacheKey = String.format("%f_%f_%f_%f",
                request.getStartX(), request.getStartY(),
                request.getEndX(), request.getEndY());

        // 캐시된 결과 확인
        CachedTransportResult cachedResult = transportTimeCache.get(cacheKey);
        if (cachedResult != null && !isCacheExpired(cachedResult) && cachedResult.getTransit() != null) {
            log.info("캐시에서 대중교통 시간 정보 반환: {}", cacheKey);
            return cachedResult.getTransit();
        }

        // 일일 API 호출 제한 확인 및 리셋
        checkAndResetDailyCounter();

        // 이미 일일 한도에 도달했다면
        if (transitApiCallCounter >= TRANSIT_API_DAILY_LIMIT) {
            log.warn("대중교통 API 일일 호출 제한에 도달했습니다 ({}회)", TRANSIT_API_DAILY_LIMIT);
            return null;
        }

        // 🔒 대중교통만 계산할 때도 락 사용
        ReentrantLock lock = apiCallLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());

        try {
            lock.lock();
            log.info("🔒 대중교통 API 호출 락 획득: {}", cacheKey);

            // 락 획득 후 캐시 재확인
            cachedResult = transportTimeCache.get(cacheKey);
            if (cachedResult != null && !isCacheExpired(cachedResult) && cachedResult.getTransit() != null) {
                log.info("🎯 락 획득 후 캐시에서 대중교통 시간 정보 반환: {}", cacheKey);
                return cachedResult.getTransit();
            }

            Integer transitTime = calculateTransitTimeInternal(request);

            // 캐시 업데이트 (기존 값 유지하면서 transit만 업데이트)
            transportTimeCache.compute(cacheKey, (k, existing) -> {
                if (existing == null) {
                    return new CachedTransportResult(null, null, transitTime, System.currentTimeMillis());
                } else {
                    return new CachedTransportResult(
                            existing.getWalking(), existing.getDriving(), transitTime, System.currentTimeMillis()
                    );
                }
            });

            return transitTime;

        } finally {
            lock.unlock();
            log.info("🔓 대중교통 API 호출 락 해제: {}", cacheKey);

            if (!lock.hasQueuedThreads()) {
                apiCallLocks.remove(cacheKey, lock);
            }
        }
    }

    /**
     * 대중교통 API 상태 확인
     */
    public Map<String, Object> getTransitApiStatus() {
        checkAndResetDailyCounter();

        Map<String, Object> status = new HashMap<>();
        status.put("available", transitApiCallCounter < TRANSIT_API_DAILY_LIMIT);
        status.put("remainingCalls", TRANSIT_API_DAILY_LIMIT - transitApiCallCounter);
        status.put("totalLimit", TRANSIT_API_DAILY_LIMIT);

        return status;
    }

    /**
     * 도보 이동시간 계산 (내부용 - 락 보호 하에서 호출)
     */
    private Integer calculateWalkingTimeInternal(TransportTimeRequest request) {
        try {
            String url = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json";

            // Content-Type을 application/x-www-form-urlencoded로 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "application/x-www-form-urlencoded");
            headers.set("appKey", tmapAppKey);

            // API 키 디버깅 출력 (앞 4자리만 표시)
            String apiKeyPrefix = tmapAppKey != null && tmapAppKey.length() > 4
                    ? tmapAppKey.substring(0, 4) + "..." : "null";
            log.info("🚶 도보 API 요청 시작 - API 키: {} (스레드: {})", apiKeyPrefix, Thread.currentThread().getName());

            // MultiValueMap을 사용하여 form 데이터 형식으로 요청
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("version", "1");  // 필수 파라미터 추가
            formData.add("startX", request.getStartX().toString());
            formData.add("startY", request.getStartY().toString());
            formData.add("endX", request.getEndX().toString());
            formData.add("endY", request.getEndY().toString());
            formData.add("reqCoordType", "WGS84GEO");
            formData.add("resCoordType", "WGS84GEO");
            formData.add("startName", URLEncoder.encode("출발지", "UTF-8"));
            formData.add("endName", URLEncoder.encode("도착지", "UTF-8"));

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            long endTime = System.currentTimeMillis();
            log.info("🚶 도보 API 호출 완료: {}ms 소요", (endTime - startTime));

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());

                if (root.has("features") && root.get("features").size() > 0) {
                    JsonNode properties = root.path("features").get(0).path("properties");
                    if (properties.has("totalTime")) {
                        double totalTimeSeconds = properties.path("totalTime").asDouble();
                        int timeInMinutes = (int) Math.ceil(totalTimeSeconds / 60.0);
                        log.info("🚶 도보 이동시간 계산 성공: {}초 -> {}분", totalTimeSeconds, timeInMinutes);
                        return timeInMinutes;
                    } else {
                        log.warn("도보 API 응답에 totalTime 필드가 없습니다.");
                    }
                } else {
                    log.warn("도보 API 응답에 features 배열이 없거나 비어있습니다.");
                }
            } else {
                log.warn("도보 API 응답 코드: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("도보 이동시간 계산 실패: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 자차 이동시간 계산 (내부용 - 락 보호 하에서 호출)
     */
    private Integer calculateDrivingTimeInternal(TransportTimeRequest request) {
        try {
            String url = "https://apis.openapi.sk.com/tmap/routes?version=1&format=json";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "application/json");
            headers.set("appKey", tmapAppKey);

            Map<String, Object> body = new HashMap<>();
            body.put("startX", request.getStartX().toString());
            body.put("startY", request.getStartY().toString());
            body.put("endX", request.getEndX().toString());
            body.put("endY", request.getEndY().toString());
            body.put("reqCoordType", "WGS84GEO");
            body.put("resCoordType", "WGS84GEO");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.info("🚗 자차 API 호출 시작 (스레드: {})", Thread.currentThread().getName());
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                double totalTimeSeconds = root.path("features").get(0).path("properties").path("totalTime").asDouble();
                int timeInMinutes = (int) Math.ceil(totalTimeSeconds / 60.0);
                log.info("🚗 자차 이동시간 계산 성공: {}초 -> {}분", totalTimeSeconds, timeInMinutes);
                return timeInMinutes;
            }
        } catch (Exception e) {
            log.error("자차 이동시간 계산 실패: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 대중교통 이동시간 계산 (내부용 - 락 보호 하에서 호출)
     *
     * ⚠️ 임시 주석 처리: API 호출 제한으로 인한 조치
     */
    private Integer calculateTransitTimeInternal(TransportTimeRequest request) {
        log.info("🚌 대중교통 API 호출 - 임시로 null 반환 (API 제한으로 인해 주석 처리됨)");

        /* API 호출 제한으로 인해 임시 주석 처리
        try {
            String url = "https://apis.openapi.sk.com/transit/routes/sub";

            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/json");
            headers.set("Content-Type", "application/json");
            headers.set("appKey", tmapTransitAppKey);

            Map<String, Object> body = new HashMap<>();
            body.put("startX", request.getStartX().toString());
            body.put("startY", request.getStartY().toString());
            body.put("endX", request.getEndX().toString());
            body.put("endY", request.getEndY().toString());
            body.put("count", 1);
            body.put("format", "json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // API 호출 카운터 증가
            transitApiCallCounter++;
            log.info("🚌 대중교통 API 실제 호출 시작 ({}번째/일일 제한 {}회) - 스레드: {}",
                    transitApiCallCounter, TRANSIT_API_DAILY_LIMIT, Thread.currentThread().getName());

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());

                // 에러 코드 확인
                JsonNode metaData = root.path("metaData");
                if (metaData.has("code") && metaData.path("code").asInt() != 0) {
                    int errorCode = metaData.path("code").asInt();
                    String errorMessage = metaData.path("message").asText();
                    log.warn("대중교통 API 에러 (코드: {}, 메시지: {})", errorCode, errorMessage);

                    if (errorCode == 11) {
                        log.info("출발지와 도착지가 가까워 대중교통 정보가 없습니다. 도보를 권장합니다.");
                    }

                    return null;
                }

                // 정상 응답 처리
                double totalTimeSeconds = metaData.path("plan").path("itineraries").get(0).path("totalTime").asDouble();
                Integer transitTime = (int) Math.ceil(totalTimeSeconds / 60.0);

                log.info("🚌 대중교통 API 호출 성공 - 결과: {}분", transitTime);
                return transitTime;
            }
        } catch (Exception e) {
            log.error("대중교통 이동시간 계산 실패: {}", e.getMessage(), e);
        }
        */

        return null; // 임시로 null 반환
    }

    /**
     * 일일 API 호출 카운터 확인 및 필요시 리셋
     */
    private synchronized void checkAndResetDailyCounter() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCounterResetTime > 86_400_000) {
            transitApiCallCounter = 0;
            lastCounterResetTime = currentTime;
            log.info("대중교통 API 일일 호출 카운터 리셋됨");
        }
    }

    /**
     * 캐시된 결과가 유효한지 확인
     */
    private boolean isCacheExpired(CachedTransportResult result) {
        return (System.currentTimeMillis() - result.getTimestamp()) > CACHE_VALIDITY_PERIOD;
    }

    /**
     * 캐시를 위한 내부 클래스
     */
    private static class CachedTransportResult {
        private final Integer walking;
        private final Integer driving;
        private final Integer transit;
        private final long timestamp;

        public CachedTransportResult(Integer walking, Integer driving, Integer transit, long timestamp) {
            this.walking = walking;
            this.driving = driving;
            this.transit = transit;
            this.timestamp = timestamp;
        }

        public Integer getWalking() {
            return walking;
        }

        public Integer getDriving() {
            return driving;
        }

        public Integer getTransit() {
            return transit;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}