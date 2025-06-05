package com.example.demo.service;

import com.example.demo.dto.Chat.ChatRequest;
import com.example.demo.dto.Chat.ChatResponse;
import com.example.demo.dto.Schedule.CreateScheduleRequest;
import com.example.demo.entity.schedule.Schedule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ScheduleService scheduleService;
    private final RestTemplate restTemplate;

    @Value("${exaone.api.url}")
    private String exaoneApiUrl;

    // 사용자별 session_id 저장 (실제 서비스에서는 Redis/DB 권장)
    private final Map<Long, String> sessionMap = new ConcurrentHashMap<>();

    public ChatResponse processMessage(ChatRequest request) {
        try {
            String sessionId = getOrCreateSessionId(request.getUserId());
            Map<String, Object> exaoneResponse = callExaoneApi(request.getMessage(), sessionId, request.getUserId());

            String intentRaw = (String) exaoneResponse.get("intent");
            String responseText = (String) exaoneResponse.get("response"); // AI의 자연어 답변
            Map<String, Object> slots = (Map<String, Object>) exaoneResponse.get("slots");

            // intent가 없으면 자연어 답변만 반환
            if (intentRaw == null) {
                return ChatResponse.builder()
                        .message(responseText != null ? responseText : "AI가 답변을 반환하지 않았습니다.")
                        .success(true)
                        .build();
            }

            String intent = mapIntent(intentRaw);

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
            return ChatResponse.builder()
                    .message("처리 중 오류가 발생했습니다: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }

    // session_id가 없으면 FastAPI /chat 호출해서 생성
    private String getOrCreateSessionId(Long userId) {
        if (sessionMap.containsKey(userId)) {
            return sessionMap.get(userId);
        }
        // FastAPI /chat 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("query", "안녕"); // 아무 메시지나, 세션 생성용
        body.put("session_id", null);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String chatUrl = exaoneApiUrl + "/chat";
        ResponseEntity<Map> response = restTemplate.postForEntity(chatUrl, entity, Map.class);
        String sessionId = (String) response.getBody().get("session_id");
        sessionMap.put(userId, sessionId);
        return sessionId;
    }

    private Map<String, Object> callExaoneApi(String message, String sessionId, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", message);
        requestBody.put("session_id", sessionId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String parseUrl = exaoneApiUrl + "/parse";
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(parseUrl, entity, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                sessionMap.remove(userId);
                String newSessionId = getOrCreateSessionId(userId);
                return callExaoneApi(message, newSessionId, userId);
            }
            throw e;
        }
    }

    private ChatResponse handleCreateSchedule(Long userId, Map<String, Object> slots) {
        try {
            CreateScheduleRequest scheduleRequest = new CreateScheduleRequest();
            scheduleRequest.setTitle((String) slots.get("title"));

            // EXAONE 응답에 맞게 datetime 필드 사용
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime dateTime = LocalDateTime.parse((String) slots.get("datetime"), formatter);
            scheduleRequest.setStartTime(dateTime);
            scheduleRequest.setEndTime(dateTime.plusHours(1)); // 예시: 1시간짜리 일정

            scheduleRequest.setLocation((String) slots.get("location"));
            scheduleRequest.setMemo((String) slots.getOrDefault("memo", ""));
            scheduleRequest.setCategory("PERSONAL"); // 기본값 설정

            Schedule schedule = scheduleService.createSchedule(
                userId,
                scheduleRequest.getTitle(),
                scheduleRequest.getStartTime(),
                scheduleRequest.getEndTime(),
                scheduleRequest.getLocation(),
                scheduleRequest.getMemo(),
                scheduleRequest.getCategory()
            );

            return ChatResponse.builder()
                    .message("일정이 성공적으로 등록되었습니다.")
                    .intent("CREATE_SCHEDULE")
                    .action("created")
                    .data(schedule)
                    .success(true)
                    .build();
        } catch (Exception e) {
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