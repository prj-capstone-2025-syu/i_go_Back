package com.example.demo.service;

import com.example.demo.dto.chat.ChatRequest;
import com.example.demo.dto.chat.ChatResponse;
import com.example.demo.dto.schedule.CreateScheduleRequest;
import com.example.demo.entity.schedule.Schedule;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    @Value("${openai.model}")
    private String openaiModel;

    @Value("${openai.max.tokens}")
    private int maxTokens;

    @Value("${openai.temperature}")
    private double temperature;

    // 사용자별 대화 히스토리 저장 (Redis로 수정 예정)
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

            log.debug("Processing chat message: {}", request.getMessage());

            // OpenAI 파인튜닝 모델 호출
            String aiResponse = callOpenAI(request.getMessage(), request.getUserId());

            // AI 응답에서 의도와 슬롯 정보 추출
            Map<String, Object> parsedResponse = parseAIResponse(aiResponse);

            String intent = (String) parsedResponse.get("intent");
            String responseText = (String) parsedResponse.get("response");
            Map<String, Object> slots = (Map<String, Object>) parsedResponse.get("slots");

            log.debug("OpenAI response - intent: {}, response: {}", intent, responseText);

            // intent가 없으면 자연어 답변만 반환
            if (intent == null || intent.equals("GENERAL")) {
                return ChatResponse.builder()
                        .message(responseText != null ? responseText : aiResponse)
                        .success(true)
                        .build();
            }

            switch (intent) {
                case "CREATE_SCHEDULE":
                    return handleCreateSchedule(request.getUserId(), slots);
                case "QUERY_SCHEDULE":
                    return handleQuerySchedule(request.getUserId(), slots);
                case "DELETE_SCHEDULE":
                    return handleDeleteSchedule(request.getUserId(), slots);
                default:
                    return ChatResponse.builder()
                            .message(responseText != null ? responseText : "죄송합니다. 이해하지 못했습니다.")
                            .success(false)
                            .build();
            }
        } catch (Exception e) {
            log.error("Error processing chat message: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .message("처리 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    private String callOpenAI(String message, Long userId) {
        try {
            // 대화 히스토리 가져오기
            List<ChatMessage> messages = getConversationHistory(userId);

            // 시스템 프롬프트 추가 (파인튜닝 모델에 맞는 프롬프트)
            if (messages.isEmpty()) {
                ChatMessage systemMessage = new ChatMessage("system",
                    "당신은 IGO 앱의 일정 관리 도우미입니다. 사용자의 요청을 분석하여 다음 형식으로 응답해주세요:\n" +
                    "INTENT: [CREATE_SCHEDULE|QUERY_SCHEDULE|DELETE_SCHEDULE|GENERAL]\n" +
                    "SLOTS: {\"title\": \"값\", \"datetime\": \"yyyy-MM-ddTHH:mm\", \"location\": \"값\", \"memo\": \"값\", \"supplies\": \"값\"}\n" +
                    "RESPONSE: 사용자에게 보여줄 자연어 응답\n\n" +
                    "일정 생성, 조회, 삭제와 관련된 요청이 아니면 INTENT를 GENERAL로 설정하고 자연스러운 대화를 해주세요.");
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

            log.debug("OpenAI API response: {}", response);
            return response;

        } catch (Exception e) {
            log.error("Error calling OpenAI API: {}", e.getMessage(), e);
            return "AI 서비스에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
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

    private ChatResponse handleCreateSchedule(Long userId, Map<String, Object> slots) {
        try {
            // Log the entire slots map to understand its structure
            log.debug("Handling CREATE_SCHEDULE with slots: {}", slots);

            CreateScheduleRequest scheduleRequest = new CreateScheduleRequest();
            if (slots != null) {
                scheduleRequest.setTitle((String) slots.get("title"));
                scheduleRequest.setLocation((String) slots.get("location"));

                // EXAONE 응답에 맞게 datetime 필드 사용
                Object datetimeObj = slots.get("datetime");
                if (datetimeObj instanceof String) {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                    LocalDateTime dateTime = LocalDateTime.parse((String) datetimeObj, formatter);
                    scheduleRequest.setStartTime(dateTime);
                    scheduleRequest.setEndTime(dateTime.plusHours(1)); // 예시: 1시간짜리 일정
                } else {
                    log.warn("Datetime field is missing or not a String in slots: {}", slots);
                    // Consider setting a default or throwing an error
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
                        if (!supplies.isEmpty()) log.debug("ChatService: Used 'supplies' (List) from Exaone slots. Value: '{}'", supplies);
                    } catch (Exception e) { log.warn("ChatService: Error processing 'supplies' as List from Exaone slots: {}", e.getMessage());}
                }

                if (supplies.isEmpty()) {
                    Object itemsVal = slots.get("items");
                    if (itemsVal instanceof String && !((String) itemsVal).isEmpty()) {
                        supplies = (String) itemsVal;
                        if (!supplies.isEmpty()) log.debug("ChatService: Used 'items' (String) from Exaone slots for supplies. Value: '{}'", supplies);
                    } else if (itemsVal instanceof List) {
                        try {
                            List<?> list = (List<?>) itemsVal;
                            supplies = list.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
                            if (!supplies.isEmpty()) log.debug("ChatService: Used 'items' (List) from Exaone slots for supplies. Value: '{}'", supplies);
                        } catch (Exception e) { log.warn("ChatService: Error processing 'items' as List from Exaone slots: {}", e.getMessage());}
                    }
                }
                scheduleRequest.setSupplies(supplies);
                log.debug("Extracted memo: '{}', supplies: '{}' from slots after checking fallbacks", memo, supplies);

            } else {
                log.warn("Slots map is null in handleCreateSchedule. Cannot extract schedule details.");
                // Return an error response or a response indicating missing information
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
                scheduleRequest.getLocation(),
                scheduleRequest.getMemo(),
                scheduleRequest.getCategory(),
                scheduleRequest.getSupplies() // supplies 전달
            );

            return ChatResponse.builder()
                    .message("일정이 성공적으로 등록되었습니다.")
                    .intent("CREATE_SCHEDULE")
                    .action("created")
                    .data(schedule)
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("Error creating schedule: {}", e.getMessage(), e); // Log specific error
            return ChatResponse.builder()
                    .message("일정 생성 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
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
            case "create_event": return "CREATE_SCHEDULE";
            case "get_schedule": return "QUERY_SCHEDULE";
            case "delete_event": return "DELETE_SCHEDULE";
            default: return intent;
        }
    }
}
