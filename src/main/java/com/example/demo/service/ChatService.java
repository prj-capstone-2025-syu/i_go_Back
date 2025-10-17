package com.example.demo.service;

import com.example.demo.dto.chat.ChatRequest;
import com.example.demo.dto.chat.ChatResponse;
import com.example.demo.dto.schedule.CreateScheduleRequest;
import com.example.demo.entity.schedule.Schedule;
import com.example.demo.entity.routine.Routine;
import com.example.demo.repository.RoutineRepository;
import com.example.demo.repository.UserRepository;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    private final ScheduleService scheduleService;
    private final OpenAiService openAiService;
    private final GeocodingService geocodingService;
    private final RoutineRepository routineRepository;
    private final UserRepository userRepository;

    @Value("${openai.model}")
    private String openaiModel;

    @Value("${openai.max.tokens}")
    private int maxTokens;

    @Value("${openai.temperature}")
    private double temperature;

    // 사용자별 대화 히스토리 저장
    private final Map<Long, List<ChatMessage>> conversationHistory = new ConcurrentHashMap<>();

    public ChatResponse processMessage(ChatRequest request) {
        try {
            // userId가 null인 경우 처리
            if (request.getUserId() == null) {
                log.warn("User ID is null in chat request");
                return ChatResponse.builder()
                        .message("로그인이 필요한 서비스입니다.")
                        .success(false)
                        .build();
            }

            // 현재 시간이 없으면 서버 시간으로 설정
            if (request.getCurrentTime() == null) {
                request.setCurrentTime(LocalDateTime.now());
            }

            log.debug("Processing chat message: {}, current time: {}", request.getMessage(), request.getCurrentTime());

            String aiResponse = callFineTunedModel(request.getMessage(), request.getUserId(), request.getCurrentTime());

            log.debug("AI response: {}", aiResponse);

            // 응답 파싱 및 처리
            return handleFineTunedResponse(aiResponse, request.getUserId());

        } catch (Exception e) {
            log.error("Error processing chat message: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("처리 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * 파인튜닝된 모델 호출 (기존 callOpenAI 메소드 사용)
     */
    private String callFineTunedModel(String message, Long userId, LocalDateTime currentTime) {
        try {
            // 대화 히스토리 가져오기
            List<ChatMessage> messages = getConversationHistory(userId);

            // 시스템 프롬프트 추가 (파인튜닝 모델에 맞는 프롬프트)
            if (messages.isEmpty()) {
                String systemPrompt = String.format(
                        "당신은 IGO 앱의 일정 관리 도우미입니다. 현재 시간은 %s입니다.\n" +
                                "사용자의 요청을 분석하여 다음 형식으로 응답해주세요:\n" +
                                "INTENT: [CREATE_SCHEDULE|QUERY_SCHEDULE|DELETE_SCHEDULE|FIND_MIDPOINT|GENERAL]\n" +
                                "SLOTS: {\"title\": \"값\", \"datetime\": \"yyyy-MM-ddTHH:mm\", \"location\": \"값\", \"memo\": \"값\", \"supplies\": \"값\", \"locations\": [\"위치1\", \"위치2\"], \"purpose\": \"값\"}\n" +
                                "RESPONSE: 사용자에게 보여줄 자연어 응답\n\n" +
                                "일정 생성, 조회, 삭제와 관련된 요청이 아니면 INTENT를 GENERAL로 설정하고 자연스러운 대화를 해주세요.\n" +
                                "중간위치나 만남 장소 찾기 요청이면 INTENT를 FIND_MIDPOINT로 설정해주세요.\n" +
                                "'내일', '모레', '다음주' 등의 상대적 시간은 현재 시간을 기준으로 절대 시간으로 변환해주세요.",
                        currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                );
                ChatMessage systemMessage = new ChatMessage("system", systemPrompt);
                messages.add(systemMessage);
            }

            // 사용자 메시지 추가
            messages.add(new ChatMessage("user", message));

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .model(openaiModel)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(completionRequest);
            String response = result.getChoices().get(0).getMessage().getContent();

            // AI 응답을 대화 히스토리에 추가
            messages.add(new ChatMessage("assistant", response));

            // 히스토리 크기 제한 (최근 20개 메시지만 유지)
            if (messages.size() > 20) {
                messages = messages.subList(messages.size() - 20, messages.size());
            }
            conversationHistory.put(userId, messages);

            log.debug("Fine-tuned model response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Error calling fine-tuned model: {}", e.getMessage(), e);
            return "AI 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
    }

    /**
     * 파인튜닝된 모델의 응답을 파싱하고 적절한 액션 수행
     */
    private ChatResponse handleFineTunedResponse(String aiResponse, Long userId) {
        try {
            // JSON 형태의 응답인지 먼저 확인
            if (isJsonResponse(aiResponse)) {
                return handleJsonResponse(aiResponse, userId);
            }

            // 기존 구조화된 응답 파싱
            Map<String, Object> parsed = parseAIResponse(aiResponse);
            String intent = (String) parsed.get("intent");
            Map<String, Object> slots = (Map<String, Object>) parsed.get("slots");
            String response = (String) parsed.get("response");

            log.debug("Parsed intent: {}, slots: {}", intent, slots);

            // Intent에 따른 처리
            if ("CREATE_SCHEDULE".equals(intent)) {
                return handleCreateSchedule(userId, slots);
            } else if ("QUERY_SCHEDULE".equals(intent)) {
                return handleQuerySchedule(userId, slots);
            } else if ("DELETE_SCHEDULE".equals(intent)) {
                return handleDeleteSchedule(userId, slots);
            } else {
                // 일반 대화
                return ChatResponse.builder()
                        .message(response != null ? response : extractCleanMessage(aiResponse))
                        .success(true)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error handling fine-tuned response: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("응답 처리 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * JSON 형태의 응답인지 확인
     */
    private boolean isJsonResponse(String response) {
        String trimmed = response.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.contains("\"title\"") && trimmed.contains("\"datetime\""));
    }

    /**
     * JSON 형태의 응답 처리
     */
    private ChatResponse handleJsonResponse(String jsonResponse, Long userId) {
        try {
            log.debug("Handling JSON response: {}", jsonResponse);

            // JSON에서 순수한 부분만 추출
            String cleanJson = extractJsonFromResponse(jsonResponse);
            Map<String, Object> jsonData = parseJsonResponse(cleanJson);

            // title과 datetime이 있으면 일정 생성으로 처리
            if (jsonData.containsKey("title") && jsonData.containsKey("datetime")) {
                return handleCreateSchedule(userId, jsonData);
            }

            // datetime만 있으면 일정 조회로 처리
            if (jsonData.containsKey("datetime") && !jsonData.containsKey("title")) {
                return handleQuerySchedule(userId, jsonData);
            }

            // 기본적으로 일반 응답으로 처리
            return ChatResponse.builder()
                    .message(extractCleanMessage(jsonResponse))
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error handling JSON response: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("JSON 응답 처리 중 오류가 발생했습니다.")
                    .success(false)
                    .build();
        }
    }

    /**
     * 응답에서 JSON 부분만 추출
     */
    private String extractJsonFromResponse(String response) {
        // 중괄호로 둘러싸인 JSON 부분 찾기
        Pattern pattern = Pattern.compile("\\{[^{}]*\\}");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return response.trim();
    }

    /**
     * JSON 문자열을 Map으로 파싱
     */
    private Map<String, Object> parseJsonResponse(String jsonStr) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 간단한 JSON 파싱 (ObjectMapper 대신)
            jsonStr = jsonStr.trim();
            if (jsonStr.startsWith("{") && jsonStr.endsWith("}")) {
                jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
            }

            String[] pairs = jsonStr.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim().replaceAll("\"", "");
                    if (!value.equals("null") && !value.isEmpty()) {
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing JSON response: {}", e.getMessage());
        }
        return result;
    }

    private ChatResponse handleCreateSchedule(Long userId, Map<String, Object> slots) {
        try {
            log.debug("Handling CREATE_SCHEDULE with slots: {}", slots);

            CreateScheduleRequest scheduleRequest = new CreateScheduleRequest();
            if (slots != null) {
                scheduleRequest.setTitle((String) slots.get("title"));

                // location 필드 처리 및 좌표 변환
                String locationInfo = (String) slots.get("location");
                if (locationInfo != null && !locationInfo.trim().isEmpty()) {
                    String[] locations = parseLocationInfo(locationInfo);
                    if (locations.length == 2) {
                        String startLocation = locations[0];
                        String destination = locations[1];

                        // 출발지 좌표 변환
                        var startCoords = geocodingService.getCoordinates(startLocation);
                        if (startCoords != null) {
                            scheduleRequest.setStartX(startCoords.getLat());
                            scheduleRequest.setStartY(startCoords.getLng());
                            scheduleRequest.setStartLocation(startLocation);
                            log.debug("출발지 좌표 변환 성공: {}, ({}, {})", startLocation, startCoords.getLat(), startCoords.getLng());
                        } else {
                            log.warn("출발지 좌표 변환 실패: {}", startLocation);
                            scheduleRequest.setStartLocation(startLocation);
                        }

                        // 도착지 좌표 변환
                        var destCoords = geocodingService.getCoordinates(destination);
                        if (destCoords != null) {
                            scheduleRequest.setDestinationX(destCoords.getLat());
                            scheduleRequest.setDestinationY(destCoords.getLng());
                            scheduleRequest.setLocation(destination);
                            log.debug("도착지 좌표 변환 성공: {}, ({}, {})", destination, destCoords.getLat(), destCoords.getLng());
                        } else {
                            log.warn("도착지 좌표 변환 실패: {}", destination);
                            scheduleRequest.setLocation(destination);
                        }
                    } else {
                        // 도착지만 있는 경우
                        var coords = geocodingService.getCoordinates(locationInfo);
                        if (coords != null) {
                            scheduleRequest.setDestinationX(coords.getLat());
                            scheduleRequest.setDestinationY(coords.getLng());
                            scheduleRequest.setLocation(locationInfo);
                            log.debug("단일 위치 좌표 변환 성공: {}, ({}, {})", locationInfo, coords.getLat(), coords.getLng());
                        } else {
                            log.warn("단일 위치 좌표 변환 실패: {}", locationInfo);
                            scheduleRequest.setLocation(locationInfo);
                        }
                    }
                }

                // EXAONE 응답에 맞게 datetime 필드 사용
                Object datetimeObj = slots.get("datetime");
                if (datetimeObj instanceof String) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                    LocalDateTime dateTime = LocalDateTime.parse((String) datetimeObj, formatter);
                    scheduleRequest.setStartTime(dateTime);
                    scheduleRequest.setEndTime(dateTime.plusHours(1));
                }

                String memo = "";
                Object memoVal = slots.get("memo");
                if (memoVal instanceof String && !((String) memoVal).isEmpty()) {
                    memo = (String) memoVal;
                } else {
                    Object notesVal = slots.get("notes");
                    if (notesVal instanceof String && !((String) notesVal).isEmpty()) {
                        memo = (String) notesVal;
                        log.debug("ChatService: Used 'notes' from Exaone slots for memo. Value: '{}'", memo);
                    } else {
                        Object descriptionVal = slots.get("description");
                        if (descriptionVal instanceof String && !((String) descriptionVal).isEmpty()) {
                            memo = (String) descriptionVal;
                            log.debug("ChatService: Used 'description' from Exaone slots for memo. Value: '{}'", memo);
                        }
                    }
                }
                scheduleRequest.setMemo(memo);

                String supplies = "";
                Object suppliesVal = slots.get("supplies");
                if (suppliesVal instanceof String && !((String) suppliesVal).isEmpty()) {
                    supplies = (String) suppliesVal;
                } else if (suppliesVal instanceof List) {
                    try {
                        List<?> list = (List<?>) suppliesVal;
                        supplies = list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                        if (!supplies.isEmpty())
                            log.debug("ChatService: Used 'supplies' (List) from Exaone slots. Value: '{}'", supplies);
                    } catch (Exception e) {
                        log.warn("ChatService: Error processing 'supplies' as List from Exaone slots: {}", e.getMessage());
                    }
                }

                if (supplies.isEmpty()) {
                    Object itemsVal = slots.get("items");
                    if (itemsVal instanceof String && !((String) itemsVal).isEmpty()) {
                        supplies = (String) itemsVal;
                        if (!supplies.isEmpty())
                            log.debug("ChatService: Used 'items' (String) from Exaone slots for supplies. Value: '{}'", supplies);
                    } else if (itemsVal instanceof List) {
                        try {
                            List<?> list = (List<?>) itemsVal;
                            supplies = list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                            if (!supplies.isEmpty())
                                log.debug("ChatService: Used 'items' (List) from Exaone slots for supplies. Value: '{}'", supplies);
                        } catch (Exception e) {
                            log.warn("ChatService: Error processing 'items' as List from Exaone slots: {}", e.getMessage());
                        }
                    }
                }
                scheduleRequest.setSupplies(supplies);
                log.debug("Extracted memo: '{}', supplies: '{}' from slots after checking fallbacks", memo, supplies);

            } else {
                log.warn("Slots map is null in handleCreateSchedule. Cannot extract schedule details.");
                return ChatResponse.builder()
                        .message("일정 생성에 필요한 정보가 부족합니다.")
                        .success(false)
                        .build();
            }
            scheduleRequest.setCategory("PERSONAL"); // 기본값 설정

            Schedule schedule = scheduleService.createSchedule(
                    userId,
                    scheduleRequest.getTitle(),
                    scheduleRequest.getStartTime(),
                    scheduleRequest.getEndTime(),
                    scheduleRequest.getStartLocation(),
                    scheduleRequest.getLocation(),
                    scheduleRequest.getMemo(),
                    scheduleRequest.getCategory(),
                    scheduleRequest.getSupplies(),
                    scheduleRequest.getStartX(),
                    scheduleRequest.getStartY(),
                    scheduleRequest.getDestinationX(),
                    scheduleRequest.getDestinationY()
            );

            return ChatResponse.builder()
                    .message("일정이 성공적으로 등록되었습니다.")
                    .intent("CREATE_SCHEDULE")
                    .action("created")
                    .data(schedule)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Error creating schedule: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("일정 생성 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    /**
     * "출발지에서 도착지" 형태의 문자열을 파싱하여 [출발지, 도착지] 배열로 반환
     */
    private String[] parseLocationInfo(String locationInfo) {
        if (locationInfo == null || locationInfo.trim().isEmpty()) {
            return new String[0];
        }

        String trimmed = locationInfo.trim();

        // "에서" 패턴으로 분리
        if (trimmed.contains("에서")) {
            String[] parts = trimmed.split("에서", 2);
            if (parts.length == 2) {
                String startLocation = parts[0].trim();
                String endLocation = parts[1].trim();

                // "로" 또는 "으로" 제거
                endLocation = endLocation.replaceAll("(으)?로$", "").trim();

                return new String[]{startLocation, endLocation};
            }
        }

        // "에서" 패턴이 없으면 다른 패턴들도 시도
        // "→", "->", "~" 등의 패턴
        String[] separators = {"→", "->", "~", " to ", " - "};
        for (String separator : separators) {
            if (trimmed.contains(separator)) {
                String[] parts = trimmed.split(separator, 2);
                if (parts.length == 2) {
                    return new String[]{parts[0].trim(), parts[1].trim()};
                }
            }
        }

        // 분리할 수 없으면 빈 배열 반환 (전체를 도착지로 처리하게 됨)
        return new String[0];
    }

    private ChatResponse handleQuerySchedule(Long userId, Map<String, Object> slots) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            String datetimeStr = (String) slots.get("datetime");

            LocalDateTime startTime, endTime;

            if (datetimeStr != null) {
                // ISO 형식 날짜가 들어오면 해당 날짜의 하루 전체 조회
                LocalDateTime date = LocalDateTime.parse(datetimeStr, formatter);
                startTime = date.withHour(0).withMinute(0);
                endTime = date.withHour(23).withMinute(59);
            } else {
                // 날짜 정보가 없으면 오늘로 처리
                LocalDateTime now = LocalDateTime.now();
                startTime = now.withHour(0).withMinute(0);
                endTime = now.withHour(23).withMinute(59);
            }

            var schedules = scheduleService.getSchedulesByDateRange(userId, startTime, endTime);
            return ChatResponse.builder()
                    .message("조회된 일정 목록입니다.")
                    .intent("QUERY_SCHEDULE")
                    .action("queried")
                    .data(schedules)
                    .success(true)
                    .build();

        } catch (Exception e) {
            return ChatResponse.builder()
                    .message("일정 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private ChatResponse handleDeleteSchedule(Long userId, Map<String, Object> slots) {
        try {
            String title = (String) slots.get("title");
            String datetime = (String) slots.get("datetime");
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

            // 1. 제목+시간 모두 있을 때
            if (title != null && datetime != null) {
                LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
                List<Schedule> candidates = scheduleService.findSchedulesByTitleAndTime(userId, title, dateTime);
                if (candidates.size() == 1) {
                    scheduleService.deleteSchedule(userId, candidates.get(0).getId());
                    return ChatResponse.builder()
                            .message("일정이 성공적으로 삭제되었습니다.")
                            .intent("DELETE_SCHEDULE")
                            .action("deleted")
                            .success(true)
                            .build();
                } else if (candidates.isEmpty()) {
                    return ChatResponse.builder()
                            .message("해당 제목과 시간에 일치하는 일정이 없습니다.")
                            .success(false)
                            .build();
                } else {
                    return ChatResponse.builder()
                            .message("해당 제목과 시간에 여러 일정이 있습니다. 더 구체적으로 입력해 주세요.")
                            .success(false)
                            .build();
                }
            }

            // 2. 제목만 있을 때
            if (title != null && datetime == null) {
                List<Schedule> candidates = scheduleService.findSchedulesByTitle(userId, title);
                if (candidates.size() == 1) {
                    scheduleService.deleteSchedule(userId, candidates.get(0).getId());
                    return ChatResponse.builder()
                            .message("일정이 성공적으로 삭제되었습니다.")
                            .intent("DELETE_SCHEDULE")
                            .action("deleted")
                            .success(true)
                            .build();
                } else if (candidates.isEmpty()) {
                    return ChatResponse.builder()
                            .message("해당 제목의 일정이 없습니다.")
                            .success(false)
                            .build();
                } else {
                    return ChatResponse.builder()
                            .message("해당 제목의 일정이 여러 개 있습니다. 시간도 함께 입력해 주세요.")
                            .success(false)
                            .build();
                }
            }

            // 3. 시간만 있을 때
            if (title == null && datetime != null) {
                LocalDateTime dateTime = LocalDateTime.parse(datetime, formatter);
                List<Schedule> candidates = scheduleService.findSchedulesByTime(userId, dateTime);
                if (candidates.size() == 1) {
                    scheduleService.deleteSchedule(userId, candidates.get(0).getId());
                    return ChatResponse.builder()
                            .message("일정이 성공적으로 삭제되었습니다.")
                            .intent("DELETE_SCHEDULE")
                            .action("deleted")
                            .success(true)
                            .build();
                } else if (candidates.isEmpty()) {
                    return ChatResponse.builder()
                            .message("해당 시간에 일정이 없습니다.")
                            .success(false)
                            .build();
                } else {
                    return ChatResponse.builder()
                            .message("해당 시간에 여러 일정이 있습니다. 제목도 함께 입력해 주세요.")
                            .success(false)
                            .build();
                }
            }

            // 4. 아무 정보도 없을 때
            return ChatResponse.builder()
                    .message("삭제할 일정을 특정할 수 없습니다. 제목이나 시간을 입력해 주세요.")
                    .success(false)
                    .build();

        } catch (Exception e) {
            return ChatResponse.builder()
                    .message("일정 삭제 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private String mapIntent(String intent) {
        if (intent == null) return null;
        switch (intent) {
            case "create_event":
                return "CREATE_SCHEDULE";
            case "get_schedule":
                return "QUERY_SCHEDULE";
            case "delete_event":
                return "DELETE_SCHEDULE";
            default:
                return intent;
        }
    }

    private String callOpenAIWithFunctionCall(String message, Long userId, LocalDateTime currentTime) {
        try {
            // 대화 히스토리 가져오기
            List<ChatMessage> messages = getConversationHistory(userId);

            // 시스템 프롬프트 추가 (Function Call에 최적화된 프롬프트)
            if (messages.isEmpty()) {
                String systemPrompt = String.format(
                        "당신은 IGO 앱의 일정 관리 도우미입니다. 현재 시간은 %s입니다.\n" +
                                "사용자의 요청을 분석하여 적절한 함수를 호출하거나 자연스러운 대화를 해주세요.\n\n" +
                                "일정 관리 관련 요청이면 다음 형식으로 응답해주세요:\n" +
                                "FUNCTION_CALL: [함수명]\n" +
                                "PARAMETERS: {JSON 파라미터}\n\n" +
                                "사용 가능한 함수:\n" +
                                "1. create_schedule: 일정 생성\n" +
                                "   - title (필수): 일정 제목\n" +
                                "   - datetime (필수): 일정 시간 (yyyy-MM-ddTHH:mm 형식)\n" +
                                "   - location: 위치\n" +
                                "   - memo: 메모\n" +
                                "   - supplies: 준비물\n" +
                                "2. query_schedule: 일정 조회\n" +
                                "   - datetime: 조회할 날짜 (없으면 오늘)\n" +
                                "3. delete_schedule: 일정 삭제\n" +
                                "   - title: 일정 제목\n" +
                                "   - datetime: 일정 시간\n\n" +
                                "상대적 시간 표현 (내일, 모레, 다음주 등)을 절대 시간으로 변환해주세요.\n" +
                                "일정 관리와 관련 없는 질문이면 자연스럽게 대화해주세요.",
                        currentTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                );
                ChatMessage systemMessage = new ChatMessage("system", systemPrompt);
                messages.add(systemMessage);
            }

            // 사용자 메시지 추가
            messages.add(new ChatMessage("user", message));

            ChatCompletionRequest completionRequest = ChatCompletionRequest.builder()
                    .model(openaiModel)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(completionRequest);
            String response = result.getChoices().get(0).getMessage().getContent();

            // AI 응답을 대화 히스토리에 추가
            messages.add(new ChatMessage("assistant", response));

            // 히스토리 크기 제한 (최근 20개 메시지만 유지)
            if (messages.size() > 20) {
                messages = messages.subList(messages.size() - 20, messages.size());
            }
            conversationHistory.put(userId, messages);

            log.debug("OpenAI Function Call response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Error calling OpenAI API with function call: {}", e.getMessage(), e);
            return "AI 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
    }

    private ChatResponse handleFunctionCall(String aiResponse, Long userId) {
        try {
            log.debug("Handling function call: {}", aiResponse);

            // FUNCTION_CALL과 PARAMETERS 추출
            String functionName = extractFunctionName(aiResponse);
            Map<String, Object> parameters = extractParameters(aiResponse);

            if (functionName == null) {
                log.warn("Function name not found in response: {}", aiResponse);
                return ChatResponse.builder()
                        .message("요청을 처리할 수 없습니다. 다시 시도해주세요.")
                        .success(false)
                        .build();
            }

            log.debug("Function: {}, Parameters: {}", functionName, parameters);

            switch (functionName) {
                case "create_schedule":
                    return handleCreateScheduleFunction(userId, parameters);
                case "query_schedule":
                    return handleQueryScheduleFunction(userId, parameters);
                case "delete_schedule":
                    return handleDeleteScheduleFunction(userId, parameters);
                case "update_schedule":
                    return handleUpdateScheduleFunction(userId, parameters);
                default:
                    log.warn("Unknown function: {}", functionName);
                    return ChatResponse.builder()
                            .message("알 수 없는 기능입니다: " + functionName)
                            .success(false)
                            .build();
            }
        } catch (Exception e) {
            log.error("Error handling function call: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("기능 실행 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private String extractFunctionName(String aiResponse) {
        Pattern pattern = Pattern.compile("FUNCTION_CALL:\\s*([a-zA-Z_]+)");
        Matcher matcher = pattern.matcher(aiResponse);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Map<String, Object> extractParameters(String aiResponse) {
        Map<String, Object> parameters = new HashMap<>();
        try {
            Pattern pattern = Pattern.compile("PARAMETERS:\\s*(\\{[^}]*\\})");
            Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String jsonStr = matcher.group(1);
                parameters = parseSimpleJson(jsonStr);
            }
        } catch (Exception e) {
            log.warn("Error extracting parameters: {}", e.getMessage());
        }
        return parameters;
    }

    private ChatResponse handleCreateScheduleFunction(Long userId, Map<String, Object> parameters) {
        try {
            log.debug("Creating schedule with parameters: {}", parameters);

            String title = (String) parameters.get("title");
            String datetimeStr = (String) parameters.get("datetime");
            String locationInfo = (String) parameters.get("location");
            String memo = (String) parameters.get("memo");
            String supplies = (String) parameters.get("supplies");
            String routineName = (String) parameters.get("routine");  // 루틴 이름 추가

            if (title == null || title.trim().isEmpty()) {
                return ChatResponse.builder()
                        .message("일정 제목이 필요합니다.")
                        .success(false)
                        .build();
            }

            if (datetimeStr == null || datetimeStr.trim().isEmpty()) {
                return ChatResponse.builder()
                        .message("일정 시간이 필요합니다.")
                        .success(false)
                        .build();
            }

            LocalDateTime startTime;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                startTime = LocalDateTime.parse(datetimeStr, formatter);
            } catch (Exception e) {
                return ChatResponse.builder()
                        .message("날짜 형식이 올바르지 않습니다. (예: 2025-10-02T14:00)")
                        .success(false)
                        .build();
            }

            LocalDateTime endTime = startTime.plusHours(1); // 기본 1시간

            // 루틴 이름으로 루틴 찾기
            Long routineId = null;
            if (routineName != null && !routineName.trim().isEmpty()) {
                try {
                    Optional<Routine> routineOpt = routineRepository.findByUserIdAndNameIgnoreCase(userId, routineName.trim());
                    if (routineOpt.isPresent()) {
                        routineId = routineOpt.get().getId();
                        log.info("루틴 '{}' 찾음. ID: {}", routineName, routineId);
                    } else {
                        log.warn("루틴 '{}' 찾을 수 없음. 루틴 없이 일정 생성", routineName);
                    }
                } catch (Exception e) {
                    log.error("루틴 검색 중 오류: {}", e.getMessage());
                }
            }

            // location 필드 처리 - "출발지에서 도착지" 형태를 분리
            String startLocation = null;
            String location = null;
            Double startX = 0.0, startY = 0.0, destinationX = 0.0, destinationY = 0.0;

            if (locationInfo != null && !locationInfo.trim().isEmpty()) {
                String[] locations = parseLocationInfo(locationInfo);
                if (locations.length == 2) {
                    startLocation = locations[0];
                    location = locations[1];
                    log.debug("Parsed locations - Start: {}, Destination: {}", startLocation, location);

                    // 좌표 변환
                    var startCoords = geocodingService.getCoordinates(startLocation);
                    if (startCoords != null) {
                        startX = startCoords.getLat();
                        startY = startCoords.getLng();
                    }

                    var destCoords = geocodingService.getCoordinates(location);
                    if (destCoords != null) {
                        destinationX = destCoords.getLat();
                        destinationY = destCoords.getLng();
                    }
                } else {
                    location = locationInfo;
                    log.debug("Single location used as destination: {}", locationInfo);

                    var coords = geocodingService.getCoordinates(location);
                    if (coords != null) {
                        destinationX = coords.getLat();
                        destinationY = coords.getLng();
                    }
                }
            }

            Schedule schedule;

            // 루틴이 있으면 루틴 기반 일정 생성, 없으면 일반 일정 생성
            if (routineId != null) {
                schedule = scheduleService.createFromRoutine(
                        userId, routineId, title, startTime, endTime,
                        startLocation, startX, startY,
                        location, destinationX, destinationY,
                        memo, supplies, "PERSONAL"
                );
                log.info("루틴 기반 일정 생성 완료: {} (루틴 ID: {})", title, routineId);
            } else {
                schedule = scheduleService.createSchedule(
                        userId, title, startTime, endTime,
                        startLocation, location, memo,
                        "PERSONAL", supplies,
                        startX, startY, destinationX, destinationY
                );
                log.info("일반 일정 생성 완료: {}", title);
            }

            String responseMessage = routineId != null
                ? String.format("'%s' 일정이 '%s' 루틴과 함께 %s에 등록되었습니다.",
                    title, routineName, startTime.format(DateTimeFormatter.ofPattern("MM월 dd일 HH:mm")))
                : String.format("'%s' 일정이 %s에 등록되었습니다.",
                    title, startTime.format(DateTimeFormatter.ofPattern("MM월 dd일 HH:mm")));

            return ChatResponse.builder()
                    .message(responseMessage)
                    .intent("CREATE_SCHEDULE")
                    .action("created")
                    .data(schedule)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error in handleCreateScheduleFunction: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("일정 생성 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private ChatResponse handleQueryScheduleFunction(Long userId, Map<String, Object> parameters) {
        try {
            String datetimeStr = (String) parameters.get("datetime");
            LocalDateTime startTime, endTime;

            if (datetimeStr != null && !datetimeStr.trim().isEmpty()) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                    LocalDateTime date = LocalDateTime.parse(datetimeStr, formatter);
                    startTime = date.withHour(0).withMinute(0);
                    endTime = date.withHour(23).withMinute(59);
                } catch (Exception e) {
                    // 날짜 파싱 실패시 오늘로 처리
                    LocalDateTime now = LocalDateTime.now();
                    startTime = now.withHour(0).withMinute(0);
                    endTime = now.withHour(23).withMinute(59);
                }
            } else {
                // 날짜 정보가 없으면 오늘로 처리
                LocalDateTime now = LocalDateTime.now();
                startTime = now.withHour(0).withMinute(0);
                endTime = now.withHour(23).withMinute(59);
            }

            var schedules = scheduleService.getSchedulesByDateRange(userId, startTime, endTime);
            String dateStr = startTime.format(DateTimeFormatter.ofPattern("MM월 dd일"));

            return ChatResponse.builder()
                    .message(String.format("%s 일정 %d개를 조회했습니다.", dateStr, schedules.size()))
                    .intent("QUERY_SCHEDULE")
                    .action("queried")
                    .data(schedules)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error in handleQueryScheduleFunction: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("일정 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private ChatResponse handleDeleteScheduleFunction(Long userId, Map<String, Object> parameters) {
        try {
            String title = (String) parameters.get("title");
            String datetimeStr = (String) parameters.get("datetime");

            if (title == null || title.trim().isEmpty()) {
                return ChatResponse.builder()
                        .message("삭제할 일정의 제목이 필요합니다.")
                        .success(false)
                        .build();
            }

            List<Schedule> candidates;
            if (datetimeStr != null && !datetimeStr.trim().isEmpty()) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                    LocalDateTime dateTime = LocalDateTime.parse(datetimeStr, formatter);
                    candidates = scheduleService.findSchedulesByTitleAndTime(userId, title, dateTime);
                } catch (Exception e) {
                    candidates = scheduleService.findSchedulesByTitle(userId, title);
                }
            } else {
                candidates = scheduleService.findSchedulesByTitle(userId, title);
            }

            if (candidates.isEmpty()) {
                return ChatResponse.builder()
                        .message(String.format("'%s' 제목의 일정을 찾을 수 없습니다.", title))
                        .success(false)
                        .build();
            } else if (candidates.size() > 1) {
                return ChatResponse.builder()
                        .message(String.format("'%s' 제목의 일정이 %d개 있습니다. 시간도 함께 입력해 주세요.", title, candidates.size()))
                        .success(false)
                        .build();
            } else {
                Schedule schedule = candidates.get(0);
                scheduleService.deleteSchedule(userId, schedule.getId());
                return ChatResponse.builder()
                        .message(String.format("'%s' 일정이 삭제되었습니다.", title))
                        .intent("DELETE_SCHEDULE")
                        .action("deleted")
                        .success(true)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error in handleDeleteScheduleFunction: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("일정 삭제 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private ChatResponse handleUpdateScheduleFunction(Long userId, Map<String, Object> parameters) {
        try {
            log.debug("Updating schedule with parameters: {}", parameters);

            // 수정할 일정 찾기
            String scheduleIdStr = (String) parameters.get("schedule_id");
            String title = (String) parameters.get("title");
            String datetimeStr = (String) parameters.get("datetime");

            // schedule_id가 있으면 ID로 찾기, 없으면 title+datetime으로 찾기
            Schedule existingSchedule = null;

            if (scheduleIdStr != null) {
                try {
                    Long scheduleId = Long.parseLong(scheduleIdStr);
                    existingSchedule = scheduleService.getScheduleById(userId, scheduleId);
                } catch (NumberFormatException e) {
                    return ChatResponse.builder()
                            .message("잘못된 일정 ID입니다.")
                            .success(false)
                            .build();
                }
            } else if (title != null) {
                // 제목으로 일정 찾기
                List<Schedule> candidates;
                if (datetimeStr != null) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                    LocalDateTime dateTime = LocalDateTime.parse(datetimeStr, formatter);
                    candidates = scheduleService.findSchedulesByTitleAndTime(userId, title, dateTime);
                } else {
                    candidates = scheduleService.findSchedulesByTitle(userId, title);
                }

                if (candidates.isEmpty()) {
                    return ChatResponse.builder()
                            .message(String.format("'%s' 제목의 일정을 찾을 수 없습니다.", title))
                            .success(false)
                            .build();
                } else if (candidates.size() > 1) {
                    return ChatResponse.builder()
                            .message(String.format("'%s' 제목의 일정이 %d개 있습니다. 시간도 함께 입력해 주세요.", title, candidates.size()))
                            .success(false)
                            .build();
                } else {
                    existingSchedule = candidates.get(0);
                }
            } else {
                return ChatResponse.builder()
                        .message("수정할 일정을 특정할 수 없습니다. 일정 ID 또는 제목을 입력해 주세요.")
                        .success(false)
                        .build();
            }

            // 수정할 필드 추출 (기존 값 유지)
            String newTitle = (String) parameters.getOrDefault("new_title", existingSchedule.getTitle());
            String newDatetimeStr = (String) parameters.get("new_datetime");
            String locationInfo = (String) parameters.get("location");
            String memo = (String) parameters.getOrDefault("memo", existingSchedule.getMemo());
            String supplies = (String) parameters.getOrDefault("supplies", existingSchedule.getSupplies());
            String routineName = (String) parameters.get("routine");

            LocalDateTime newStartTime = existingSchedule.getStartTime();
            LocalDateTime newEndTime = existingSchedule.getEndTime();

            if (newDatetimeStr != null && !newDatetimeStr.trim().isEmpty()) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                newStartTime = LocalDateTime.parse(newDatetimeStr, formatter);
                newEndTime = newStartTime.plusHours(1); // 기본 1시간
            }

            // 루틴 이름으로 루틴 찾기
            Long routineId = existingSchedule.getRoutineId();
            if (routineName != null && !routineName.trim().isEmpty()) {
                Optional<Routine> routineOpt = routineRepository.findByUserIdAndNameIgnoreCase(userId, routineName.trim());
                if (routineOpt.isPresent()) {
                    routineId = routineOpt.get().getId();
                    log.info("루틴 '{}' 찾음. ID: {}", routineName, routineId);
                } else {
                    log.warn("루틴 '{}' 찾을 수 없음.", routineName);
                    return ChatResponse.builder()
                            .message(String.format("'%s' 루틴을 찾을 수 없습니다.", routineName))
                            .success(false)
                            .build();
                }
            }

            // location 필드 처리
            String startLocation = existingSchedule.getStartLocation();
            String location = existingSchedule.getLocation();
            Double startX = existingSchedule.getStartX();
            Double startY = existingSchedule.getStartY();
            Double destinationX = existingSchedule.getDestinationX();
            Double destinationY = existingSchedule.getDestinationY();

            if (locationInfo != null && !locationInfo.trim().isEmpty()) {
                String[] locations = parseLocationInfo(locationInfo);
                if (locations.length == 2) {
                    startLocation = locations[0];
                    location = locations[1];

                    // 좌표 변환
                    var startCoords = geocodingService.getCoordinates(startLocation);
                    if (startCoords != null) {
                        startX = startCoords.getLat();
                        startY = startCoords.getLng();
                    }

                    var destCoords = geocodingService.getCoordinates(location);
                    if (destCoords != null) {
                        destinationX = destCoords.getLat();
                        destinationY = destCoords.getLng();
                    }
                } else {
                    location = locationInfo;

                    var coords = geocodingService.getCoordinates(location);
                    if (coords != null) {
                        destinationX = coords.getLat();
                        destinationY = coords.getLng();
                    }
                }
            }

            // 일정 수정
            Schedule updatedSchedule = scheduleService.updateSchedule(
                    userId, existingSchedule.getId(), routineId,
                    newTitle, newStartTime, newEndTime,
                    startLocation, startX, startY,
                    location, destinationX, destinationY,
                    memo, supplies, "PERSONAL"
            );

            return ChatResponse.builder()
                    .message(String.format("'%s' 일정이 수정되었습니다.", newTitle))
                    .intent("UPDATE_SCHEDULE")
                    .action("updated")
                    .data(updatedSchedule)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error in handleUpdateScheduleFunction: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("일정 수정 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private List<ChatMessage> getConversationHistory(Long userId) {
        return conversationHistory.getOrDefault(userId, new ArrayList<>());
    }

    private Map<String, Object> parseAIResponse(String aiResponse) {
        Map<String, Object> result = new HashMap<>();

        try {
            // INTENT 추출
            Pattern intentPattern = Pattern.compile("INTENT:\\s*([A-Z_]+)");
            Matcher intentMatcher = intentPattern.matcher(aiResponse);
            if (intentMatcher.find()) {
                result.put("intent", intentMatcher.group(1));
            }

            // SLOTS 추출 (JSON 형태)
            Pattern slotsPattern = Pattern.compile("SLOTS:\\s*(\\{[^}]+\\})");
            Matcher slotsMatcher = slotsPattern.matcher(aiResponse);
            if (slotsMatcher.find()) {
                String slotsJson = slotsMatcher.group(1);
                // 간단한 JSON 파싱 (실제로는 ObjectMapper 사용 권장)
                Map<String, Object> slots = parseSimpleJson(slotsJson);
                result.put("slots", slots);
            }

            // RESPONSE 추출
            Pattern responsePattern = Pattern.compile("RESPONSE:\\s*(.+?)(?=\\n|$)");
            Matcher responseMatcher = responsePattern.matcher(aiResponse);
            if (responseMatcher.find()) {
                result.put("response", responseMatcher.group(1).trim());
            } else {
                // RESPONSE가 없으면 전체 응답을 사용
                result.put("response", aiResponse);
            }

        } catch (Exception e) {
            log.warn("Error parsing AI response: {}", e.getMessage());
            result.put("response", aiResponse);
        }

        return result;
    }

    private Map<String, Object> parseSimpleJson(String jsonStr) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 간단한 JSON 파싱 (실제 프로덕션에서는 ObjectMapper 사용 권장)
            jsonStr = jsonStr.replaceAll("[{}]", "");
            String[] pairs = jsonStr.split(",");

            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim().replaceAll("\"", "");
                    if (!value.equals("null") && !value.isEmpty()) {
                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing simple JSON: {}", e.getMessage());
        }
        return result;
    }

    /**
     * AI 응답에서 불필요한 JSON 형태나 구조화된 부분을 제거하고 순수한 메시지만 추출
     */
    private String extractCleanMessage(String aiResponse) {
        try {
            // 1. {"response": "메시지"} 형태 처리
            if (aiResponse.contains("\"response\"")) {
                Pattern pattern = Pattern.compile("\"response\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(aiResponse);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            // 2. RESPONSE: 메시지 형태 처리
            if (aiResponse.contains("RESPONSE:")) {
                Pattern responsePattern = Pattern.compile("RESPONSE:\\s*(.+?)(?=\\n(?:INTENT|SLOTS|ACTION)|$)", Pattern.DOTALL);
                Matcher responseMatcher = responsePattern.matcher(aiResponse);
                if (responseMatcher.find()) {
                    String message = responseMatcher.group(1).trim();
                    // JSON 중괄호 제거
                    message = message.replaceAll("^\\{|\\}$", "").trim();
                    return message;
                }
            }

            // 3. 전체가 JSON 형태인 경우 처리
            String trimmed = aiResponse.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                // response 필드만 추출
                Pattern jsonPattern = Pattern.compile("\"response\"\\s*:\\s*\"([^\"]+)\"");
                Matcher jsonMatcher = jsonPattern.matcher(trimmed);
                if (jsonMatcher.find()) {
                    return jsonMatcher.group(1);
                }

                // message 필드 추출 시도
                Pattern msgPattern = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");
                Matcher msgMatcher = msgPattern.matcher(trimmed);
                if (msgMatcher.find()) {
                    return msgMatcher.group(1);
                }
            }

            // 4. 다른 구조화된 응답 패턴들 제거
            String cleaned = aiResponse;

            // INTENT:, SLOTS:, ACTION: 등의 구조화된 부분 제거
            cleaned = cleaned.replaceAll("INTENT:\\s*[A-Z_]+\\s*\n?", "");
            cleaned = cleaned.replaceAll("SLOTS:\\s*\\{[^}]*\\}\\s*\n?", "");
            cleaned = cleaned.replaceAll("ACTION:\\s*[^\\n]*\\s*\n?", "");

            // 남은 중괄호 제거
            cleaned = cleaned.replaceAll("^\\s*\\{\\s*", "");
            cleaned = cleaned.replaceAll("\\s*\\}\\s*$", "");

            // 앞뒤 공백 및 개행 정리
            cleaned = cleaned.trim();

            // 빈 문자열이 아니면 정리된 메시지 반환
            if (!cleaned.isEmpty() && !cleaned.equals(aiResponse.trim())) {
                return cleaned;
            }

            // 원본 응답에서 마지막으로 따옴표 안의 텍스트 추출 시도
            Pattern lastQuotePattern = Pattern.compile("\"([^\"]+)\"[^\"]*$");
            Matcher lastQuoteMatcher = lastQuotePattern.matcher(aiResponse);
            if (lastQuoteMatcher.find()) {
                String extracted = lastQuoteMatcher.group(1);
                // 필드명이 아닌 실제 메시지인지 확인
                if (!extracted.matches("response|message|intent|action|slots|title|datetime|location|memo|supplies")) {
                    return extracted;
                }
            }

            return aiResponse.trim();

        } catch (Exception e) {
            log.warn("Error extracting clean message from AI response: {}", e.getMessage());
            return aiResponse.trim();
        }
    }
}
