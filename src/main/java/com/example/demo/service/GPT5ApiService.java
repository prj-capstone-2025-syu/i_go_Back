package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GPT5ApiService {

    @Value("${openai.api.key}")
    private String openAIApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * GPT-5 API 직접 호출 (max_completion_tokens 지원) ->
     */
    public String callGPT5(String model, String systemPrompt, int maxCompletionTokens, double temperature) {
        try {
            String url = "https://api.openai.com/v1/chat/completions";

            // 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAIApiKey);

            // 요청 본문 생성
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt)
            ));
            requestBody.put("max_completion_tokens", maxCompletionTokens); // GPT-5 파라미터
            requestBody.put("temperature", temperature);

            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            log.debug("GPT-5 API Request: {}", requestJson);

            // API 호출
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String content = responseJson.path("choices").get(0).path("message").path("content").asText();
                log.info("GPT-5 API 호출 성공");
                return content;
            } else {
                log.error("GPT-5 API 호출 실패: HTTP {}", response.getStatusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("GPT-5 API 호출 중 오류: {}", e.getMessage(), e);
            return null;
        }
    }
}
