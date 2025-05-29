package com.example.demo.service;

import com.example.demo.dto.Chat.ChatRequest;
import com.example.demo.dto.Chat.ChatResponse;
import com.example.demo.dto.Schedule.CreateScheduleRequest;
import com.example.demo.entity.schedule.Schedule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ScheduleService scheduleService;
    private final RestTemplate restTemplate;

    @Value("${exaone.api.url}")
    private String exaoneApiUrl;

    public ChatResponse processMessage(ChatRequest request) {
        try {
            // EXAONE API 호출
            Map<String, Object> exaoneResponse = callExaoneApi(request.getMessage());
            
            // 응답에서 intent와 slot 추출
            String intent = mapIntent((String) exaoneResponse.get("intent"));
            Map<String, Object> slots = (Map<String, Object>) exaoneResponse.get("slots");

            // intent에 따른 처리
            switch (intent) {
                case "CREATE_SCHEDULE":
                    return handleCreateSchedule(request.getUserId(), slots);
                case "QUERY_SCHEDULE":
                    return handleQuerySchedule(request.getUserId(), slots);
                case "DELETE_SCHEDULE":
                    return handleDeleteSchedule(request.getUserId(), slots);
                default:
                    return ChatResponse.builder()
                            .message("죄송합니다. 이해하지 못했습니다.")
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

    private Map<String, Object> callExaoneApi(String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("query", message);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForObject(exaoneApiUrl, request, Map.class);
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