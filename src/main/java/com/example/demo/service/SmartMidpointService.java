package com.example.demo.service;

import com.example.demo.dto.midpoint.RecommendedStation;
import com.example.demo.dto.midpoint.*; // Coordinates, MidpointResponse, RecommendedStation, GooglePlace 포함
import com.example.demo.dto.odsay.OdsaySearchStationResponse; // 신규 DTO
import com.example.demo.exception.LocationNotFoundException;
import lombok.Data; // Lombok @Data import
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmartMidpointService {

    private final GeocodingService geocodingService; // 좌표 변환용
    private final MidpointService midpointService; // Google Places 검색용
    private final OdysseyTransitService odysseyTransitService; // ODsay API 호출용
    private final GPT5ApiService gpt5ApiService; // GPT5 직접 호출용


    @Value("${gpt5.Mini.model}")
    private String gpt5MiniModel;
    @Value("${gpt5.Mini.max.tokens}")
    private int gpt5MiniMaxTokens;
    @Value("${gpt5.Mini.temperature}")
    private double gpt5MiniTemperature;

    private final Map<Long, MidpointSession> userSessions = new ConcurrentHashMap<>();

    // --- processMidpointRequest 및 세션 처리 메소드들 ---
    public Mono<MidpointResponse> processMidpointRequest(Long userId, String userMessage) {
        try {
            MidpointSession session = userSessions.getOrDefault(userId, new MidpointSession());
            log.info("Processing midpoint request for user {}: message='{}', session state='{}'",
                    userId, userMessage, session.getState());

            return switch (session.getState()) {
                case INITIAL -> Mono.just(handleInitialRequest(userId, session));
                case WAITING_FOR_COUNT -> Mono.just(handlePersonCountInput(userId, userMessage, session));
                case COLLECTING_LOCATIONS -> handleLocationInputAndRecommend(userId, userMessage, session); // 마지막 상태
                default -> Mono.just(resetAndStartOver(userId)); // 예외 상태
            };
        } catch (Exception e) {
            log.error("Error processing midpoint request: {}", e.getMessage(), e);
            userSessions.remove(userId); // 오류 시 세션 정리
            return Mono.just(MidpointResponse.builder()
                    .success(false)
                    .message("처리 중 오류가 발생했습니다. 처음부터 다시 시도해주세요.")
                    .build());
        }
    }

     private MidpointResponse handleInitialRequest(Long userId, MidpointSession session) {
        session.setState(MidpointSession.SessionState.WAITING_FOR_COUNT);
        userSessions.put(userId, session);
        return MidpointResponse.builder()
                .success(true)
                .message("중간 만남 장소를 찾아드리겠습니다! 🚇\n\n" +
                        "먼저 총 몇 명이 만나실 예정인가요?\n" +
                        "(예: 3명, 최대 6명까지 가능)")
                .build();
    }

    private MidpointResponse handlePersonCountInput(Long userId, String userMessage, MidpointSession session) {
         try {
            int count = extractPersonCount(userMessage);

            // [추가] 최소 인원 검증
            if (count < 2) {
                log.warn("User {} entered invalid person count (less than 2): {}", userId, count);
                return MidpointResponse.builder()
                        .success(false)
                        .message("최소 2명 이상이어야 합니다. 다시 인원수를 알려주세요.")
                        .build();
            }

            if (count > 6) {
                log.warn("User {} entered person count exceeding the limit (max 6): {}", userId, count);
                return MidpointResponse.builder()
                        .success(false)
                        .message(String.format("죄송합니다, 현재 최대 %d명까지만 지원합니다. 다시 인원수를 알려주세요.", 6))
                        .build();
            }

            // 검증 통과 시 세션 업데이트 및 다음 단계 안내
            session.setTotalPersons(count);
            session.setState(MidpointSession.SessionState.COLLECTING_LOCATIONS);
            userSessions.put(userId, session);
            log.info("User {} set person count to {}", userId, count);
            return MidpointResponse.builder()
                    .success(true)
                    .message(String.format("총 %d명이 만나시는군요! 👥\n\n" +
                            "이제 각자의 출발 위치를 알려주세요.\n" +
                            "(예: 강남역, 홍대입구역, 신림역)", count))
                    .build();

        } catch (IllegalArgumentException e) { // 숫자 파싱 실패 시
             log.warn("User {} entered non-numeric person count: {}", userId, userMessage);
            return MidpointResponse.builder()
                    .success(false)
                    .message(e.getMessage() + "\n(예: 3명, 5명 또는 숫자만 입력)")
                    .build();
        }
    }

    // 위치 입력 처리 및 충분하면 바로 추천 시작
    private Mono<MidpointResponse> handleLocationInputAndRecommend(Long userId, String userMessage, MidpointSession session) {
        //파싱
        ParseResult parseResult = parseLocationsFromMessage(userMessage);
        List<String> rawLocations = parseResult.stations; // '역'이 포함된 것으로 판단된 항목들
        List<String> missingSuffixes = parseResult.missingSuffixes; // '역'이 없는 항목들 (재입력 요구)

        if (rawLocations.isEmpty() && missingSuffixes.isEmpty()) {
            return Mono.just(MidpointResponse.builder().success(false).message("위치를 입력해주세요. (예: 강남역)").build());
        }

        // rawLocations에 대해 지오코딩 검증
        return Flux.fromIterable(rawLocations)
                .flatMap(location -> Mono.fromCallable(() -> { // GeocodingService.getCoordinates가 동기이므로 Callable로 감싸기
                            try {
                                Coordinates coords = geocodingService.getCoordinates(location);
                                return Map.entry(location, coords != null); // 성공 여부 반환
                            } catch (Exception e) { // 혹시 모를 예외 처리
                                log.error("Error geocoding '{}': {}", location, e.getMessage());
                                return Map.entry(location, false);
                            }
                        })
                )
                .collectList()
                .flatMap(results -> {
                    // 결과 처리 및 세션 업데이트
                    List<String> validLocations = results.stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).collect(Collectors.toList());
                    List<String> invalidLocations = results.stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).collect(Collectors.toList());

                    if (!validLocations.isEmpty()) {
                        Set<String> currentLocations = new HashSet<>(session.getCollectedLocations());
                        currentLocations.addAll(validLocations);
                        session.setCollectedLocations(new ArrayList<>(currentLocations));
                        log.info("User {} added valid locations: {}", userId, validLocations);
                    }

                    int collected = session.getCollectedLocations().size();
                    int needed = session.getTotalPersons();

                    StringBuilder responseMessage = new StringBuilder();
                    if (!missingSuffixes.isEmpty()) {
                        responseMessage.append(String.format("❗ 다음 입력에는 '역'을 포함해주세요: %s\n\n", String.join(", ", missingSuffixes)));
                    }
                    if (!invalidLocations.isEmpty()) responseMessage.append(String.format("❌ '%s' 위치는 찾을 수 없었어요. 더 자세한 주소나 장소명으로 다시 시도해주세요.\n\n", String.join(", ", invalidLocations)));

                    // 충분한 위치가 수집되었는지 확인
                    if (collected >= needed) {
                        userSessions.remove(userId); // 추천 시작 시 세션 종료
                        List<String> finalLocations = session.getCollectedLocations().stream().limit(needed).collect(Collectors.toList());
                        //responseMessage.append(String.format("모든 위치(%d/%d) 수집 완료!\n수집된 위치: %s\n\n🔍 환승 편리한 역을 찾는 중...", collected, needed, String.join(", ", finalLocations)));
                        log.info("All locations collected for user {}. Starting recommendation...", userId);

                        // 추천 로직 호출
                        return calculateAndRecommendHybrid(finalLocations)
                                .map(res -> {
                                    // 최종 메시지 앞에 진행 메시지 추가
                                    res.setMessage(responseMessage.toString() + res.getMessage());
                                    return res;
                                });
                    } else {
                        // 위치 더 필요
                        responseMessage.append(String.format("현재 수집된 위치 (%d/%d):\n%s\n\n추가로 %d개의 위치가 더 필요합니다.", collected, needed, String.join(", ", session.getCollectedLocations()), needed - collected));
                        userSessions.put(userId, session); // 세션 업데이트
                        log.info("User {} needs {} more locations.", userId, needed - collected);
                        return Mono.just(MidpointResponse.builder().success(true).message(responseMessage.toString()).build());
                    }
                });
    }

    // 위치 파싱 결과를 담는 내부 유틸 클래스
    private static class ParseResult {
        List<String> stations = new ArrayList<>();
        List<String> missingSuffixes = new ArrayList<>();
    }

    /**
     * 사용자 메시지에서 역명을 추출합니다.
     * - '강남역', '강남 역' 등의 입력을 합쳐서 하나의 역명으로 인식합니다.
     * - 쉼표(,), 세미콜론(;) 또는 줄바꿈으로 구분된 경우 여러 역을 추출합니다.
     * - 개별 토큰 중에서 '역'이 붙어있지 않은 항목들은 missingSuffixes에 담아 재입력 요청에 사용합니다.
     */
    private ParseResult parseLocationsFromMessage(String message) {
        ParseResult result = new ParseResult();
        if (message == null) return result;

        // 토큰화: 쉼표/세미콜론/줄바꿈으로 먼저 분리
        String[] segments = message.split("[,;，；\\n\\r]+");

        for (String seg : segments) {
            if (seg == null) continue;
            seg = seg.trim();
            if (seg.isEmpty()) continue;

            // 내부적으로 공백으로 나뉘어 있을 수 있는 "강남 역" 같은 경우를 처리하기 위해 내부 토큰 분해
            String[] parts = seg.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                String token = parts[i].trim();
                if (token.isEmpty()) continue;

                // 만약 토큰 자체가 '역'이면 앞 토큰과 합친다
                if (token.equals("역") && i > 0) {
                    String prev = parts[i - 1].trim();
                    if (!prev.isEmpty()) {
                        String combined = prev + "역";
                        if (!result.stations.contains(combined)) {
                            result.stations.add(combined);
                        }
                        parts[i - 1] = "";
                    }
                    continue;
                }

                // 토큰이 이미 '역'으로 끝나는 경우 (예: '강남역')
                if (token.endsWith("역")) {
                    String cleaned = token.replaceAll("[\\p{Punct}]+$", "").trim();
                    if (!cleaned.isEmpty() && !result.stations.contains(cleaned)) result.stations.add(cleaned);
                    continue;
                }

                // 토큰이 단독으로 '역'이 붙어있지 않고 다음 토큰이 '역'일 경우 합친다
                if (i + 1 < parts.length && parts[i + 1].trim().equals("역")) {
                    String combined = token + "역";
                    if (!result.stations.contains(combined)) result.stations.add(combined);
                    i++;
                    continue;
                }

                // 토큰이 '역' 접미사 없이 단독으로 존재하면 missingSuffixes에 추가
                if (token.length() > 1) {
                    if (token.matches("^[\\p{L}0-9가-힣]+$")) {
                        result.missingSuffixes.add(token);
                    }
                }
            }
        }

        // 추가 보정: stations에 들어간 항목 중에 여전히 공백 포함(예: '서울 강남역')되면 정리
        result.stations = result.stations.stream().map(s -> s.replaceAll("\\s+", " ").trim()).filter(s -> s.length() > 1).collect(Collectors.toList());
        // missingSuffixes 중 stations에 포함된 항목은 제거
        result.missingSuffixes = result.missingSuffixes.stream().filter(s -> result.stations.stream().noneMatch(st -> st.contains(s))).distinct().collect(Collectors.toList());

        return result;
    }

    // [핵심 로직] Google Places + ODsay 하이브리드 방식
    private Mono<MidpointResponse> calculateAndRecommendHybrid(List<String> locations) {
        log.info("Hybrid recommendation started for locations: {}", locations);

        // 1. 모든 위치 좌표 조회 (GeocodingService 사용)
        return Flux.fromIterable(locations)
                .flatMap(location -> Mono.fromCallable(() -> geocodingService.getCoordinates(location)) // 동기 호출 래핑
                                         .map(Optional::ofNullable) // null 가능성 처리
                                         .defaultIfEmpty(Optional.empty()) // 예외 발생 시 빈 Optional
                )
                .collectList()
                .flatMap(optionalCoordinatesList -> {
                    // 유효한 좌표만 추출
                    List<Coordinates> coordinatesList = optionalCoordinatesList.stream()
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList());

                    if (coordinatesList.size() != locations.size()) {
                        log.warn("Failed to get coordinates for all locations. {} out of {}", coordinatesList.size(), locations.size());
                        // 실패한 위치 정보 포함하여 메시지 개선
                        List<String> failedLocations = new ArrayList<>(locations);
                        // coordinatesList에 있는 위치 찾아서 제거 (좌표->위치 역변환 필요 또는 다른 방식)
                        // 임시 메시지:
                        return Mono.just(MidpointResponse.builder().success(false).message("일부 위치의 좌표를 찾을 수 없습니다. 다시 시도해주세요.").build());
                    }

                    // 2. 지리적 중간 지점 계산
                    Coordinates geometricMidpoint = calculateMidpointInternal(coordinatesList);
                    log.info("Calculated geometric midpoint: lat={}, lng={}", geometricMidpoint.getLat(), geometricMidpoint.getLng());

                    // 3. 중간 지점 근처 '지하철역' 검색 (Google Places API)
                    List<GooglePlace> nearbySubwayStationsGoogle;
                    try {
                        // MidpointService의 getNearbyPlaces 호출 (동기)
                        nearbySubwayStationsGoogle = midpointService.getNearbyPlaces(geometricMidpoint, "subway_station");
                    } catch (LocationNotFoundException e) {
                        log.warn("No nearby subway stations found via Google Places: {}", e.getMessage());
                        return Mono.just(MidpointResponse.builder().success(false).message("중간 지점 근처에서 지하철역을 찾을 수 없습니다.").build());
                    } catch (Exception e) { // 그 외 Google API 호출 예외
                        log.error("Error calling Google Places API: {}", e.getMessage(), e);
                        return Mono.just(MidpointResponse.builder().success(false).message("Google Places API 호출 중 오류 발생").build());
                    }

                    if (nearbySubwayStationsGoogle.isEmpty()) {
                        log.warn("Google Places returned an empty list for subway stations.");
                        return Mono.just(MidpointResponse.builder().success(false).message("중간 지점 근처에서 지하철역을 찾을 수 없습니다.").build());
                    }
                    log.info("Found {} potential subway stations via Google Places.", nearbySubwayStationsGoogle.size());

                    // 4. Google Place -> ODsay Station ID 매핑 및 환승 정보 조회 (비동기 처리)
                    return Flux.fromIterable(nearbySubwayStationsGoogle)
                            // 각 Google Place 결과에 대해 ODsay stationID 및 환승 정보 찾기
                            .flatMap(googlePlace ->
                                findOdsayStationAndLanes(googlePlace) // 아래 정의된 헬퍼 메소드 호출
                                    .map(Optional::of) // 결과 Optional로 래핑
                                    .defaultIfEmpty(Optional.empty()) // 에러/결과 없음 시 빈 Optional
                            )
                            .filter(Optional::isPresent) // 유효한 결과만 필터링
                            .map(Optional::get)
                            .collectList()
                            .flatMap(filteredStations -> { // 5. 최종 결과 처리
                                // ... (Fallback 및 최종 응답 생성 로직은 이전 답변과 동일) ...
                                if (filteredStations.isEmpty()) {
                                    log.warn("No stations met the transfer criteria after ODsay lookup.");
                                    // Fallback: Google 결과 중 첫 번째 역의 정보만 조회해서 반환
                                    if (nearbySubwayStationsGoogle.isEmpty()) { // 혹시 모를 방어 코드
                                        return Mono.just(MidpointResponse.builder().success(false).message("추천할 지하철역을 찾지 못했습니다.").build());
                                    }
                                    GooglePlace closestGoogleStation = nearbySubwayStationsGoogle.get(0);
                                    return findOdsayStationAndLanes(closestGoogleStation) // Fallback용 재호출
                                        .map(fallbackStation -> {
                                            List<RecommendedStation> fallbackList = List.of(fallbackStation);
                                            return generateFinalResponse(locations, fallbackList, "조건에 맞는 환승역이 없어 가장 가까운 역 1곳을 추천합니다.");
                                        })
                                        .defaultIfEmpty(MidpointResponse.builder().success(false).message("가장 가까운 역의 환승 정보 조회에도 실패했습니다.").build());

                                } else {
                                    // 성공: 환승 많은 순 정렬 및 중복 제거, GPT 호출
                                    filteredStations.sort(Comparator.comparing(RecommendedStation::getLaneCount).reversed());
                                    List<RecommendedStation> distinctStations = filteredStations.stream()
                                            .collect(Collectors.collectingAndThen(
                                                    Collectors.toMap(RecommendedStation::getStationName, rs -> rs, (rs1, rs2) -> rs1.getLaneCount() >= rs2.getLaneCount() ? rs1 : rs2), // 이름 같으면 환승 많은 것 유지
                                                    map -> new ArrayList<>(map.values())
                                            ));
                                    distinctStations.sort(Comparator.comparing(RecommendedStation::getLaneCount).reversed());

                                    log.info("Filtered, distinct, and sorted recommended stations: {}", distinctStations.stream().map(RecommendedStation::getStationName).collect(Collectors.toList()));
                                    return Mono.just(generateFinalResponse(locations, distinctStations, null));
                                }
                            });
                })
                .onErrorResume(Exception.class, e -> { // 전체적인 에러 처리
                    log.error("Unexpected error during hybrid recommendation: {}", e.getMessage(), e);
                    return Mono.just(MidpointResponse.builder().success(false).message("추천 장소 검색 중 오류가 발생했습니다.").build());
                });
    }

    /**
     * Google Place 정보를 받아 가장 가까운 ODsay 지하철역 ID를 찾고,
     * 해당 역의 환승 정보를 조회하여 RecommendedStation 객체를 만드는 헬퍼 메소드 (비동기)
     */
    private Mono<RecommendedStation> findOdsayStationAndLanes(GooglePlace googlePlace) {
        String googleStationName = googlePlace.getName();
        // Google Place 좌표 가져오기 (Null 체크 추가)
        if (googlePlace.getGeometry() == null || googlePlace.getGeometry().getLocation() == null) {
             log.warn("Google Place '{}' has no geometry/location info.", googleStationName);
             // *** 반환 타입을 Mono<RecommendedStation>으로 명시 ***
             return Mono.<RecommendedStation>empty();
        }
        Coordinates coords = new Coordinates(
            googlePlace.getGeometry().getLocation().getLat(),
            googlePlace.getGeometry().getLocation().getLng()
        );

        // ODsay searchStation API 호출 (이름 기반 검색)
        return odysseyTransitService.searchStationByName(googleStationName)
             // *** flatMap의 반환 타입은 Mono여야 함 ***
            .flatMap(searchResultStations -> { // searchResultStations is List<StationInfo>
                if (searchResultStations.isEmpty()) {
                    log.warn("ODsay searchStation found no results for '{}'", googleStationName);
                    // *** Flux.empty() -> Mono.empty() ***
                    return Mono.<RecommendedStation>empty();
                }

                // 결과 중 stationClass=2(지하철)이고 Google 좌표와 가장 가까운 ODsay 역 찾기 (동기 로직)
                Optional<OdsaySearchStationResponse.StationInfo> closestOdsayStationOpt = searchResultStations.stream()
                    .filter(s -> s.getStationClass() != null && s.getStationClass() == 2)
                    .filter(s -> s.getY() != null && s.getX() != null && s.getY() != 0 && s.getX() != 0)
                    .min(Comparator.comparingDouble(s ->
                        calculateDistance(coords.getLat(), coords.getLng(), s.getY(), s.getX())
                    ));

                if (closestOdsayStationOpt.isEmpty()) {
                    log.warn("ODsay searchStation results for '{}' contained no suitable subway station.", googleStationName);
                    // Flux.empty() -> Mono.empty()
                    return Mono.<RecommendedStation>empty();
                }

                OdsaySearchStationResponse.StationInfo closestOdsayStation = closestOdsayStationOpt.get();
                if (closestOdsayStation.getStationID() == null) {
                    log.error("Found ODsay station '{}' but its stationID is null!", closestOdsayStation.getStationName());
                    // Flux.empty() -> Mono.empty()
                    return Mono.<RecommendedStation>empty();
                }
                int odsayStationId = closestOdsayStation.getStationID();
                log.info("Mapped Google Place '{}' to ODsay Station '{}' (ID: {})", googleStationName, closestOdsayStation.getStationName(), odsayStationId);

                // ODsay subwayStationInfo API 호출하여 환승 정보 얻기 (이 부분은 이미 Mono 반환)
                return odysseyTransitService.getStationInfo(odsayStationId)
                    .flatMap(stationInfoResponse -> {
                        Set<String> uniqueLanes = stationInfoResponse.collectUniqueLaneNames();
                        int laneCount = uniqueLanes.size();
                        boolean hasAirportLine = uniqueLanes.stream().anyMatch(l -> l.contains("공항철도"));
                        if (laneCount >= 2 || hasAirportLine) {
                            RecommendedStation recommended = new RecommendedStation(googleStationName, coords.getLng(), coords.getLat(), uniqueLanes, laneCount);
                            return Mono.just(recommended);
                        } else {
                            log.debug("Station '{}' (ID: {}) with {} lanes ({}) did not meet criteria.", googleStationName, odsayStationId, laneCount, uniqueLanes);
                            return Mono.<RecommendedStation>empty(); // 타입 명시
                        }
                    }); // getStationInfo flatMap 종료
            }) // searchStationByName 결과 처리 flatMap 종료
            // .next() 제거됨 (flatMap이 이미 Mono 반환)
            .onErrorResume(e -> { // findOdsayStationAndLanes 내부의 전체적인 에러 처리
                 log.error("Error in findOdsayStationAndLanes for '{}': {}", googleStationName, e.getMessage());
                 // *** 타입을 명시적으로 지정 ***
                 return Mono.<RecommendedStation>empty();
            });
    }

    // 위도, 경도 기반 거리 계산 (Haversine formula - 근사치)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        if (lat2 == 0 || lon2 == 0) return Double.MAX_VALUE; // 좌표 없으면 최대 거리

        final int R = 6371; // 지구 반지름 (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c * 1000; // 미터 단위 반환
    }


    // --- generateFinalResponse, generateAIRecommendationODsay, 유틸리티, reset ---
    // (이전 답변 코드 유지)
    private MidpointResponse generateFinalResponse(List<String> locations,
                                                    List<RecommendedStation> recommendedStations,
                                                    String fallbackMessage) {
         String topStationsText = recommendedStations.stream()
                .limit(3)
                .map(s -> String.format("- %s (%d개 노선: %s)",
                        s.getStationName(), s.getLaneCount(), s.getUniqueLanes()))
                .collect(Collectors.joining("\n"));

        String gptMessage;
        if (fallbackMessage != null) {
            gptMessage = fallbackMessage + "\n" + topStationsText;
        } else if (recommendedStations.isEmpty()) {
             gptMessage = "추천할 만한 환승역을 찾지 못했습니다. 입력한 위치를 다시 확인해주세요.";
        }
         else {
            gptMessage = generateAIRecommendationODsay(locations, recommendedStations);
        }

        // MidpointResponse DTO 구조에 맞게 수정
        // 중간 좌표 계산 다시 필요 시 추가
        Coordinates midpointCoords = null;
        try {
             // 이 시점에는 locations에 대한 좌표가 없을 수 있으므로, 다시 계산 필요 시 주의
             // List<Coordinates> coordsList = locations.stream().map(loc -> geocodingService.getCoordinates(loc)).filter(Objects::nonNull).collect(Collectors.toList());
             // if (!coordsList.isEmpty()) midpointCoords = calculateMidpointInternal(coordsList);
        } catch (Exception e) {
            log.warn("Could not recalculate midpoint for response DTO: {}", e.getMessage());
        }

        return MidpointResponse.builder()
                .success(true)
                .message(gptMessage)
                .recommendedStations(recommendedStations)
                 // 기존 필드 (선택적)
                 .midpointCoordinates(midpointCoords) // null일 수 있음
                 .midpointAddress(recommendedStations.isEmpty() ? "추천 역 없음" : recommendedStations.get(0).getStationName() + " 근처") // 대표 주소
                .build();
    }

     private String generateAIRecommendationODsay(List<String> locations,
                                                  List<RecommendedStation> candidates) {
        if (candidates.isEmpty()) {
            return "추천할 만한 환승역을 찾지 못했습니다.";
        }

        StringBuilder candidatesText = new StringBuilder();
        for (int i = 0; i < Math.min(candidates.size(), 3); i++) {
            RecommendedStation station = candidates.get(i);
            candidatesText.append(String.format("%d. 역 이름: %s, 지나는 노선: %s (%d개)\n",
                    i + 1, station.getStationName(), station.getUniqueLanes(), station.getLaneCount()));
        }
//당신은 "환승역 추천 요약 AI"입니다. **매우 간결하게** 답변해야 합니다.
//
//                [입력 정보]
//                - 참석자 출발 위치: %s
//                - 추천 지하철역 후보 목록 (환승 많은 순):
//                %s
//
//                [지시 사항]
//                1. 위 '추천 지하철역 후보 목록'에서 **가장 환승이 편리한 역 1곳** (최대 2곳까지만)을 선정하세요.
//                2. 선정된 각 역에 대해 다음 정보만 **간단히** 포함하여 **한두 문장**으로 추천 이유를 요약하세요:
//                   - 역 이름 ( ~역 근처는 안되고 명확히 역 이름을 말해야함)
//                   - 총 환승 가능 노선 수
//                   - 주요 노선 이름 목록 (괄호 안에 쉼표로 구분)
//                3. **절대로** 경로를 설명하거나 길게 부연 설명하지 마세요.
//                4. 최종 답변 형식 예시:
//                   "가장 추천하는 역은 **OO역**입니다. 총 N개 노선(A호선, B호선, C선) 환승이 가능하여 편리합니다."
//                   (만약 2곳 추천 시: "추천 역은 OO역과 XX역입니다. OO역은 N개 노선(...), XX역은 M개 노선(...) 환승이 가능합니다.")
        try {
            String systemPrompt = String.format("""
                당신은 "환승역 추천 요약 AI"입니다. **매우 간결하게** 답변해야 합니다.

                [입력 정보]
                - 참석자 출발 위치: %s
                - 추천 지하철역 후보 목록 (환승 많은 순):
                %s

                [지시 사항]
                1. 위 '추천 지하철역 후보 목록'에서 **가장 환승이 편리한 역 1곳** 을 선정하세요.
                2. 선정된 각 역에 대해 다음 정보만 **간단히** 포함하여 **한두 문장**으로 추천 이유를 요약하세요:
                   - 역 이름
                   - 총 환승 가능 노선 수
                   - 주요 노선 이름 목록 (괄호 안에 쉼표로 구분)
                3. **절대로** 경로를 설명하거나 길게 부연 설명하지 마세요.
                4. 최종 답변 형식 예시:
                   "가장 추천하는 역은 "OO역" 입니다. 총 N개 노선(A호선, B호선, C선) 환승이 가능하여 편리합니다."
                """,
                    String.join(", ", locations),
                    candidatesText.toString()
            );

            // ⭐ 직접 HTTP 호출로 변경
            String aiResponse = gpt5ApiService.callGPT5(
                    gpt5MiniModel,
                    systemPrompt,
                    gpt5MiniMaxTokens,
                    gpt5MiniTemperature
            );

            if (aiResponse != null) {
                log.info("GPT5 recommendation generated successfully based on ODsay station list.");
                log.info(aiResponse);
                return aiResponse;
            } else {
                throw new RuntimeException("GPT-5 API returned null");
            }

        } catch (Exception e) {
            log.error("Error generating AI recommendation using ODsay results: {}", e.getMessage(), e);
            RecommendedStation topStation = candidates.get(0);
            return String.format("AI 추천 생성에 실패했습니다.\n환승이 가장 편리한 역은 '%s'(%d개 노선: %s) 입니다.",
                    topStation.getStationName(), topStation.getLaneCount(), topStation.getUniqueLanes());
        }
    }

     private List<String> extractLocationsFromMessage(String message) {
        List<String> locations = new ArrayList<>();
        String[] parts = message.split("[,\\s\\t]+");
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty() && part.length() > 1) {
                locations.add(part);
            }
        }
        return locations;
     }

    private int extractPersonCount(String message) {
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) { /* ignore */ }
        }
        try {
            return Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
             throw new IllegalArgumentException("인원수를 파악할 수 없습니다. 숫자만 입력해주세요.");
        }
    }

     private Coordinates calculateMidpointInternal(List<Coordinates> coordinatesList) {
        if (coordinatesList == null || coordinatesList.isEmpty()) {
            throw new IllegalArgumentException("좌표 목록이 비어있습니다.");
        }
        double totalLat = 0.0, totalLng = 0.0;
        for (Coordinates coords : coordinatesList) {
            totalLat += coords.getLat();
            totalLng += coords.getLng();
        }
        return new Coordinates(totalLat / coordinatesList.size(), totalLng / coordinatesList.size());
     }

    public MidpointResponse resetAndStartOver(Long userId) {
        userSessions.remove(userId);
        return handleInitialRequest(userId, new MidpointSession());
    }

    @Data // Lombok @Data 추가
    public static class MidpointSession {
        public enum SessionState {
            INITIAL, WAITING_FOR_COUNT, COLLECTING_LOCATIONS
        }
        private SessionState state = SessionState.INITIAL;
        private int totalPersons;
        private List<String> collectedLocations = new ArrayList<>();
    }
}
