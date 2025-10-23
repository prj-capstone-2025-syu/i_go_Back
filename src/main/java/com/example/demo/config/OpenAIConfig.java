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
     * GPT-5 Mini 모델용 OpenAI 서비스 (중간역 처리)
     */
    @Bean("gpt5MiniService")
    public OpenAiService gpt5MiniService() {
        log.info("Initializing GPT-5 Mini service for future use");
        return new OpenAiService(openaiApiKey, Duration.ofSeconds(60));
    }
}
