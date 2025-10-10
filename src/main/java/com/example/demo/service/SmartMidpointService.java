package com.example.demo.service;

import com.example.demo.dto.midpoint.Coordinates;
import com.example.demo.dto.midpoint.GooglePlace;
import com.example.demo.dto.midpoint.MidpointResponse;
import com.example.demo.exception.LocationNotFoundException;
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
import java.util.stream.Collectors;

/**
 * Service for providing AI-powered smart midpoint recommendations using GPT-4
 * Implements MVP flow: ask for number of people -> locations -> purpose -> preferences
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

    private final Map<Long, MidpointSession> userSessions = new ConcurrentHashMap<>();

    public MidpointResponse processMidpointRequest(Long userId, String userMessage) {
        try {
            MidpointSession session = userSessions.getOrDefault(userId, new MidpointSession());
            log.info("Processing midpoint request for user {}: message='{}', session state='{}'",
                    userId, userMessage, session.getState());

            switch (session.getState()) {
                case INITIAL:
                    return handleInitialRequest(userId, session);
                case WAITING_FOR_COUNT:
                    return handlePersonCountInput(userId, userMessage, session);
                case COLLECTING_LOCATIONS:
                    return handleLocationInput(userId, userMessage, session);
                case WAITING_FOR_PURPOSE:
                    return handlePurposeInput(userId, userMessage, session);
                case WAITING_FOR_PREFERENCES:
                    return handlePreferencesInput(userId, userMessage, session);
                default:
                    return resetAndStartOver(userId);
            }
        } catch (Exception e) {
            log.error("Error processing midpoint request: {}", e.getMessage(), e);
            userSessions.remove(userId);
            return MidpointResponse.builder()
                    .success(false)
                    .message("처리 중 오류가 발생했습니다. 처음부터 다시 시도해주세요.")
                    .build();
        }
    }

    private MidpointResponse handleInitialRequest(Long userId, MidpointSession session) {
        session.setState(MidpointSession.SessionState.WAITING_FOR_COUNT);
        userSessions.put(userId, session);
        return MidpointResponse.builder()
                .success(true)
                .message("만남 장소를 찾아드리겠습니다! 🗺️\n\n" +
                        "먼저 총 몇 명이 만나실 예정인가요?\n" +
                        "(예: 3명, 5명)")
                .build();
    }

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
                            "(예: 강남역, 홍대입구역, 신림역)", count))
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
        List<String> rawLocations = extractLocationsFromMessage(userMessage);
        if (rawLocations.isEmpty()) {
            return MidpointResponse.builder().success(false).message("위치를 찾을 수 없습니다. (예: 강남역)").build();
        }

        List<String> validLocations = new ArrayList<>();
        List<String> invalidLocations = new ArrayList<>();

        // 입력된 위치들을 하나씩 즉시 검증
        for (String location : rawLocations) {
            try {
                // midpointService를 사용해 위치 유효성 검사
                midpointService.getCoordinatesForLocation(location);
                // 성공하면 유효한 위치 목록에 추가
                validLocations.add(location);
            } catch (LocationNotFoundException e) {
                // 실패하면 유효하지 않은 위치 목록에 추가
                invalidLocations.add(location);
            }
        }

        // 유효한 위치들만 세션에 추가
        if (!validLocations.isEmpty()) {
            session.getCollectedLocations().addAll(validLocations);
            session.setCollectedLocations(session.getCollectedLocations().stream().distinct().collect(Collectors.toList()));
        }

        int collected = session.getCollectedLocations().size();
        int needed = session.getTotalPersons();

        // 응답 메시지 생성
        StringBuilder responseMessage = new StringBuilder();
        if (!validLocations.isEmpty()) {
            responseMessage.append(String.format("✅ %s 위치가 추가되었습니다!\n\n", String.join(", ", validLocations)));
        }
        if (!invalidLocations.isEmpty()) {
            responseMessage.append(String.format("❌ '%s' 위치는 찾을 수 없었어요. 더 자세한 주소나 장소명으로 다시 시도해주세요.\n\n", String.join(", ", invalidLocations)));
        }

        // 모든 위치가 수집되었는지 확인
        if (collected >= needed) {
            session.setState(MidpointSession.SessionState.WAITING_FOR_PURPOSE);
            responseMessage.append(String.format("모든 위치(%d/%d)가 수집되었습니다.\n\n수집된 위치: %s\n\n이제 어떤 목적으로 만나시는지 알려주세요.\n(예: 회의, 식사, 스터디)",
                    collected, needed, String.join(", ", session.getCollectedLocations())));
        } else {
            responseMessage.append(String.format("현재 수집된 위치 (%d/%d):\n%s\n\n추가로 %d개의 위치가 더 필요합니다.",
                    collected, needed, String.join(", ", session.getCollectedLocations()), needed - collected));
        }

        userSessions.put(userId, session);
        return MidpointResponse.builder().success(true).message(responseMessage.toString()).build();
    }

    private MidpointResponse handlePurposeInput(Long userId, String userMessage, MidpointSession session) {
        session.setPurpose(userMessage.trim());
        session.setState(MidpointSession.SessionState.WAITING_FOR_PREFERENCES);
        userSessions.put(userId, session);

        return MidpointResponse.builder()
                .success(true)
                .message(String.format("만남 목적: '%s'\n\n" +
                        "좋습니다! 마지막으로 선호하는 장소 유형이 있다면 알려주세요.\n" +
                        "(예: 조용한 카페, 가성비 좋은 식당, 지하철역 근처)", session.getPurpose()))
                .build();
    }

    private MidpointResponse handlePreferencesInput(Long userId, String userMessage, MidpointSession session) {
        session.setPreferences(userMessage.trim());
        userSessions.remove(userId);

        List<String> finalLocations = session.getCollectedLocations().subList(0, session.getTotalPersons());
        return calculateAndRecommend(finalLocations, session.getPurpose(), session.getPreferences());
    }

    private MidpointResponse calculateAndRecommend(List<String> locations, String purpose, String preferences) {
        try {
            log.info("Calculating midpoint for: {}", locations);
            MidpointResponse basicMidpoint = midpointService.findMidpoint(locations);
            if (!basicMidpoint.isSuccess()) return basicMidpoint;

            //  MidpointService의 새 메서드를 호출하여 '후보 목록 전체'를 가져옴
            List<GooglePlace> candidates = midpointService.getNearbyPlaces(basicMidpoint.getMidpointCoordinates());

            String aiRecommendation = generateAIRecommendation(
                locations,
                basicMidpoint, // MidpointResponse 객체 전체를 넘김
                purpose,
                preferences,
                candidates // 후보 목록 전체를 넘겨줌
            );

            return MidpointResponse.builder()
                    .midpointCoordinates(basicMidpoint.getMidpointCoordinates())
                    .midpointAddress(basicMidpoint.getMidpointAddress())
                    .success(true)
                    .message(aiRecommendation)
                    .build();
        } catch (Exception e) {
            log.error("Error in calculateAndRecommend: {}", e.getMessage(), e);
            MidpointResponse fallback = MidpointResponse.builder().success(false).message("AI 추천 중 오류가 발생했습니다.").build();
            try {
                fallback = midpointService.findMidpoint(locations);
                if (fallback.isSuccess()) {
                    fallback.setMessage("기본 중간위치: " + fallback.getMidpointAddress() + "\n\n(AI 추천 기능은 일시적으로 사용할 수 없습니다)");
                }
            } catch (Exception fallbackEx) {
                log.error("Error getting fallback midpoint: {}", fallbackEx.getMessage());
            }
            return fallback;
        }
    }

    private List<String> extractLocationsFromMessage(String message) {
        List<String> locations = new ArrayList<>();
        String[] parts = message.split("[,\\s]+");
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty() && part.length() > 1) {
                locations.add(part);
            }
        }
        return locations;
    }

    private int extractPersonCount(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(message);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        throw new IllegalArgumentException("인원수를 파악할 수 없습니다.");
    }

    private String generateAIRecommendation(
            List<String> locations,
            MidpointResponse midpoint,
            String purpose,
            String preferences,
            List<GooglePlace> candidates // 후보 목록을 직접 받음
    ) {
        // 후보 목록을 프롬프트에 넣기 좋은 문자열 형태로 가공
        StringBuilder candidatesText = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            GooglePlace place = candidates.get(i);
            candidatesText.append(String.format("%d. 이름: %s, 주소: %s, 평점: %.1f\n",
                    i + 1, place.getName(), place.getVicinity(), place.getRating()));
        }

        try {
            String systemPrompt = String.format("""
                당신은 주어진 장소 목록 내에서만 답변해야 하는 "장소 추천 AI"입니다. 당신의 지식을 사용하지 마세요.

                [입력 정보]
                - 참석자들의 출발 위치: %s
                - 계산된 중간 지점: %s
                - 만남의 목적: "%s"
                - 선호하는 장소 유형: "%s"

                [!!! 가장 중요한 규칙 !!!]
                - **반드시** 아래 제공된 "장소 후보 목록" **안에서만** 3곳을 골라 추천해야 합니다.
                - 목록에 없는 장소는 **절대로** 지어내거나 언급해서는 안 됩니다.
                - 만약 목록에 있는 장소가 목적/선호도와 맞지 않더라도, 그 중에서 가장 나은 선택지를 골라 설명해야 합니다.

                [장소 후보 목록]
                %s

                [지시 사항]
                1. 위 '장소 후보 목록'을 분석하여, '만남의 목적'과 '선호하는 장소 유형'에 가장 적합한 장소 3곳을 선정하세요.
                2. 각 장소에 대해 "추천 이유"를 반드시 포함하여 설명하고, "이름", "주소", "평점" 정보를 정확히 기재하세요.
                3. 답변 형식은 아래 예시를 반드시 지켜주세요. 인사나 불필요한 말은 절대 금지합니다.

                [답변 예시]
                1. **[장소 이름]**
                   - 주소: [목록에 있는 주소]
                   - 평점: [목록에 있는 평점]
                   - 추천 이유: [목적과 선호도를 반영하여, 왜 이 목록에서 이 장소를 골랐는지 설명]
                """,
                String.join(", ", locations),
                midpoint.getMidpointAddress(),
                purpose,
                preferences,
                candidatesText.toString() // 가공된 후보 목록 문자열 주입
            );

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model(gpt4Model)
                    .messages(List.of(new ChatMessage("system", systemPrompt)))
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            ChatCompletionResult result = gpt4Service.createChatCompletion(request);
            log.debug("GPT-4 recommendation generated successfully based on provided list.");
            return result.getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            log.error("Error generating AI recommendation: {}", e.getMessage(), e);
            return String.format("AI 추천 생성에 실패했습니다. 계산된 중간 지점은 '%s' 입니다.", midpoint.getMidpointAddress());
        }
    }

    private MidpointResponse resetAndStartOver(Long userId) {
        userSessions.remove(userId);
        return handleInitialRequest(userId, new MidpointSession());
    }

    public static class MidpointSession {
        public enum SessionState {
            INITIAL, WAITING_FOR_COUNT, COLLECTING_LOCATIONS, WAITING_FOR_PURPOSE, WAITING_FOR_PREFERENCES
        }

        private SessionState state = SessionState.INITIAL;
        private int totalPersons;
        private List<String> collectedLocations = new ArrayList<>();
        private String purpose;
        private String preferences;
        
        // Getters and Setters
        public SessionState getState() { return state; }
        public void setState(SessionState state) { this.state = state; }
        public int getTotalPersons() { return totalPersons; }
        public void setTotalPersons(int totalPersons) { this.totalPersons = totalPersons; }
        public List<String> getCollectedLocations() { return collectedLocations; }
        public void setCollectedLocations(List<String> collectedLocations) { this.collectedLocations = collectedLocations; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public String getPreferences() { return preferences; }
        public void setPreferences(String preferences) { this.preferences = preferences; }
    }
}