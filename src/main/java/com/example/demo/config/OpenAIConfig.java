package com.example.demo.config;

import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Slf4j
@Configuration
public class OpenAIConfig {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${gpt4.api.key}")
    private String gpt4ApiKey;

    /**
     * 파인튜닝된 모델용 OpenAI 서비스 (기존 챗봇용)
     */
    @Bean
    @Primary
    public OpenAiService openAiService() {
        log.info("Initializing Fine-tuned OpenAI service");
        return new OpenAiService(openaiApiKey, Duration.ofSeconds(60));
    }

    /**
     * 기본 GPT-4 모델용 OpenAI 서비스 (중간위치 찾기용)
     */
    @Bean("gpt4Service")
    public OpenAiService gpt4Service() {
        log.info("Initializing GPT-4 service for midpoint calculation");
        return new OpenAiService(gpt4ApiKey, Duration.ofSeconds(60));
    }
}
