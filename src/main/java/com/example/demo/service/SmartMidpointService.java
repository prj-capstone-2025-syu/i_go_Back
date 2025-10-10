package com.example.demo.service;

import com.example.demo.dto.midpoint.Coordinates;
import com.example.demo.dto.midpoint.MidpointResponse;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for providing AI-powered smart midpoint recommendations using GPT-4
 * Implements MVP flow: ask for number of people first, then collect locations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartMidpointService {

    private final MidpointService midpointService;

    @Qualifier("gpt4Service")
    private final OpenAiService gpt4Service;

    @Value("${gpt4.model}")
    private String gpt4Model;

    @Value("${gpt4.max.tokens}")
    private int maxTokens;

    @Value("${gpt4.temperature}")
    private double temperature;

    // 사용자별 중간위치 세션 저장 (인원수, 현재 수집된 위치 등)
    private final Map<Long, MidpointSession> userSessions = new ConcurrentHashMap<>();

    /**
     * MVP 플로우에 따른 중간위치 계산 처리
     * 1단계: 인원수 확인
     * 2단계: 위치 수집
     * 3단계: 결과 계산 및 AI 추천
     */
    public MidpointResponse processMidpointRequest(Long userId, String userMessage) {
        try {
            MidpointSession session = userSessions.getOrDefault(userId, new MidpointSession());

            log.info("Processing midpoint request for user {}: message='{}', session state='{}'",
                    userId, userMessage, session.getState());

            switch (session.getState()) {
                case INITIAL:
                    return handleInitialRequest(userId, userMessage, session);

                case WAITING_FOR_COUNT:
                    return handlePersonCountInput(userId, userMessage, session);

                case COLLECTING_LOCATIONS:
                    return handleLocationInput(userId, userMessage, session);

                default:
                    return resetAndStartOver(userId);
            }

        } catch (Exception e) {
            log.error("Error processing midpoint request: {}", e.getMessage(), e);
            userSessions.remove(userId); // 세션 초기화
            return MidpointResponse.builder()
                    .success(false)
                    .message("처리 중 오류가 발생했습니다. 다시 시도해주세요.")
                    .build();
        }
    }

    /**
     * 초기 요청 처리 - 인원수 질문
     */
    private MidpointResponse handleInitialRequest(Long userId, String userMessage, MidpointSession session) {
        // 이미 위치 정보가 포함된 요청인지 확인
        if (containsLocationKeywords(userMessage)) {
            // 위치 정보가 있으면 바로 처리 시도
            List<String> extractedLocations = extractLocationsFromMessage(userMessage);
            if (extractedLocations.size() >= 2) {
                return calculateAndRecommend(extractedLocations, "만남", "편의시설 접근성");
            }
        }

        session.setState(MidpointSession.SessionState.WAITING_FOR_COUNT);
        userSessions.put(userId, session);

        return MidpointResponse.builder()
                .success(true)
                .message("만남 장소를 찾아드리겠습니다! 🗺️\n\n" +
                        "먼저 총 몇 명이 만나실 예정인가요?\n" +
                        "(예: 3명, 5명)")
                .build();
    }

    /**
     * 인원수 입력 처리
     */
    private MidpointResponse handlePersonCountInput(Long userId, String userMessage, MidpointSession session) {
        try {
            int count = extractPersonCount(userMessage);
            if (count < 2) {
                return MidpointResponse.builder()
                        .success(false)
                        .message("최소 2명 이상이어야 중간위치를 계산할 수 있습니다.\n다시 인원수를 알려주세요.")
                        .build();
            }

            session.setTotalPersons(count);
            session.setState(MidpointSession.SessionState.COLLECTING_LOCATIONS);
            userSessions.put(userId, session);

            return MidpointResponse.builder()
                    .success(true)
                    .message(String.format("총 %d명이 만나시는군요! 👥\n\n" +
                            "이제 각자의 출발 위치를 알려주세요.\n" +
                            "(예: 강남역, 홍대입구역, 신림역)\n\n" +
                            "한 번에 모든 위치를 입력하거나, 하나씩 입력해주셔도 됩니다.", count))
                    .build();

        } catch (Exception e) {
            return MidpointResponse.builder()
                    .success(false)
                    .message("인원수를 정확히 입력해주세요.\n(예: 3명, 5명)")
                    .build();
        }
    }

    /**
     * 위치 입력 처리
     */
    private MidpointResponse handleLocationInput(Long userId, String userMessage, MidpointSession session) {
        List<String> newLocations = extractLocationsFromMessage(userMessage);

        if (newLocations.isEmpty()) {
            return MidpointResponse.builder()
                    .success(false)
                    .message(String.format("위치를 찾을 수 없습니다.\n\n" +
                            "현재 수집된 위치: %d/%d\n" +
                            "위치를 정확히 입력해주세요. (예: 강남역, 홍대입구역)",
                            session.getCollectedLocations().size(), session.getTotalPersons()))
                    .build();
        }

        // 새로운 위치들을 세션에 추가
        session.getCollectedLocations().addAll(newLocations);

        // 중복 제거
        session.getCollectedLocations().stream().distinct().toList();

        int collected = session.getCollectedLocations().size();
        int needed = session.getTotalPersons();

        if (collected >= needed) {
            // 필요한 위치를 모두 수집했으면 계산 실행
            List<String> finalLocations = session.getCollectedLocations().subList(0, needed);
            userSessions.remove(userId); // 세션 정리

            return calculateAndRecommend(finalLocations, "만남", "접근성과 편의시설");
        } else {
            // 더 많은 위치 필요
            userSessions.put(userId, session);

            return MidpointResponse.builder()
                    .success(true)
                    .message(String.format("위치가 추가되었습니다! ✅\n\n" +
                            "현재 수집된 위치 (%d/%d):\n%s\n\n" +
                            "추가로 %d개의 위치가 더 필요합니다.",
                            collected, needed,
                            String.join(", ", session.getCollectedLocations()),
                            needed - collected))
                    .build();
        }
    }

    /**
     * 실제 중간위치 계산 및 AI 추천 생성
     */
    private MidpointResponse calculateAndRecommend(List<String> locations, String purpose, String preferences) {
        try {
            log.info("Calculating midpoint and generating recommendations for: {}", locations);

            // 기본 중간점 계산
            MidpointResponse basicMidpoint = midpointService.findMidpoint(locations);

            if (!basicMidpoint.isSuccess()) {
                return basicMidpoint;
            }

            // GPT-4를 사용하여 스마트 추천 생성
            String aiRecommendation = generateAIRecommendation(
                locations,
                basicMidpoint.getMidpointCoordinates(),
                basicMidpoint.getMidpointAddress(),
                purpose,
                preferences
            );

            return MidpointResponse.builder()
                    .midpointCoordinates(basicMidpoint.getMidpointCoordinates())
                    .midpointAddress(basicMidpoint.getMidpointAddress())
                    .success(true)
                    .message(aiRecommendation)
                    .build();

        } catch (Exception e) {
            log.error("Error in calculateAndRecommend: {}", e.getMessage(), e);

            // AI 추천 실패 시 기본 중간점만 반환
            MidpointResponse fallback = midpointService.findMidpoint(locations);
            if (fallback.isSuccess()) {
                fallback.setMessage("기본 중간위치: " + fallback.getMidpointAddress() +
                                  "\n\n(AI 추천 기능은 일시적으로 사용할 수 없습니다)");
            }
            return fallback;
        }
    }

    /**
     * 메시지에서 위치 정보가 포함되어 있는지 확인
     */
    private boolean containsLocationKeywords(String message) {
        String[] keywords = {"역", "구", "시", "동", "로", "거리", "에서", "까지", "중간"};
        String lowerMessage = message.toLowerCase();

        for (String keyword : keywords) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 메시지에서 위치 정보 추출
     */
    private List<String> extractLocationsFromMessage(String message) {
        List<String> locations = new ArrayList<>();

        // 쉼표나 공백으로 분리된 위치들 추출
        String[] parts = message.split("[,\\s]+");

        for (String part : parts) {
            part = part.trim();
            if (part.length() > 1 && (part.contains("역") || part.contains("구") ||
                part.contains("시") || part.contains("동") || part.length() > 2)) {
                // 숫자나 특수문자 제거
                part = part.replaceAll("[0-9]+명?", "").trim();
                if (!part.isEmpty() && part.length() > 1) {
                    locations.add(part);
                }
            }
        }

        return locations;
    }

    /**
     * 메시지에서 인원수 추출
     */
    private int extractPersonCount(String message) {
        // 숫자 + 명 패턴 찾기
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)명?");
        java.util.regex.Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        // 한글 숫자 처리
        String[] koreanNumbers = {"두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열"};
        for (int i = 0; i < koreanNumbers.length; i++) {
            if (message.contains(koreanNumbers[i])) {
                return i + 2; // "두"는 2, "세"는 3...
            }
        }

        throw new IllegalArgumentException("인원수를 파악할 수 없습니다.");
    }

    /**
     * GPT-4를 사용한 AI 추천 생성
     */
    private String generateAIRecommendation(
            List<String> locations,
            Coordinates midpoint,
            String midpointAddress,
            String purpose,
            String preferences) {

        try {
            List<ChatMessage> messages = new ArrayList<>();

            // 시스템 프롬프트 설정
            String systemPrompt = String.format(
                "당신은 서울의 지리와 교통에 정통한 만남 장소 추천 전문가입니다.\n" +
                "사용자들의 출발지: %s\n" +
                "계산된 중간 위치: %s (좌표: %.6f, %.6f)\n" +
                "만남 목적: %s\n" +
                "선호사항: %s\n\n" +
                "다음 형식으로 추천해주세요:\n" +
                "1. 📍 **추천 장소**: [구체적인 장소명]\n" +
                "2. 🚇 **교통 접근성**: [각 출발지에서의 접근 방법]\n" +
                "3. 🏢 **주변 편의시설**: [카페, 식당, 쇼핑몰 등]\n" +
                "4. 💡 **추천 이유**: [왜 이 장소가 좋은지]\n\n" +
                "실제 존재하는 서울 지역의 장소만 추천하고, 교통편과 소요시간을 구체적으로 제시해주세요.",
                String.join(", ", locations),
                midpointAddress,
                midpoint.getLat(),
                midpoint.getLng(),
                purpose,
                preferences
            );

            messages.add(new ChatMessage("system", systemPrompt));

            String userPrompt = String.format(
                "%s에서 출발하는 사람들이 만날 수 있는 최적의 장소를 추천해주세요. " +
                "중간 지점은 %s 근처입니다.",
                String.join(", ", locations),
                midpointAddress
            );

            messages.add(new ChatMessage("user", userPrompt));

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(gpt4Model)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            ChatCompletionResult result = gpt4Service.createChatCompletion(request);
            String recommendation = result.getChoices().get(0).getMessage().getContent();

            log.debug("GPT-4 recommendation generated successfully");
            return recommendation;

        } catch (Exception e) {
            log.error("Error generating AI recommendation: {}", e.getMessage(), e);
            return String.format(
                "📍 **중간 위치**: %s\n\n" +
                "계산된 중간 지점을 기준으로 만남 장소를 선택해보세요.\n" +
                "주변의 카페, 식당, 또는 지하철역 근처가 접근하기 좋을 것 같습니다!\n\n" +
                "💡 각자의 위치: %s",
                midpointAddress,
                String.join(", ", locations)
            );
        }
    }

    /**
     * 세션 초기화 및 다시 시작
     */
    private MidpointResponse resetAndStartOver(Long userId) {
        userSessions.remove(userId);
        MidpointSession newSession = new MidpointSession();
        return handleInitialRequest(userId, "", newSession);
    }

    /**
     * 사용자 세션 정보를 저장하는 내부 클래스
     */
    public static class MidpointSession {
        public enum SessionState {
            INITIAL, WAITING_FOR_COUNT, COLLECTING_LOCATIONS
        }

        private SessionState state = SessionState.INITIAL;
        private int totalPersons;
        private List<String> collectedLocations = new ArrayList<>();

        // Getters and Setters
        public SessionState getState() { return state; }
        public void setState(SessionState state) { this.state = state; }
        public int getTotalPersons() { return totalPersons; }
        public void setTotalPersons(int totalPersons) { this.totalPersons = totalPersons; }
        public List<String> getCollectedLocations() { return collectedLocations; }
        public void setCollectedLocations(List<String> collectedLocations) { this.collectedLocations = collectedLocations; }
    }
}

