package com.example.demo.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class FCMService {

    // 단일 기기에 메시지 전송
    public String sendMessageToToken(String token, String title, String body, Map<String, String> data) {
        try {
            // UTF-8 인코딩 확인 및 보정
            String encodedTitle = ensureUTF8(title);
            String encodedBody = ensureUTF8(body);

            // title과 body를 data 맵에 직접 추가
            Map<String, String> fullData = new HashMap<>(data);
            fullData.put("title", encodedTitle);
            fullData.put("body", encodedBody);

            Message message = Message.builder()
                    .putAllData(fullData) // 모든 정보를 data에 담아 전송
                    .setToken(token)
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(encodedTitle)
                                            .setBody(encodedBody)
                                            .build())
                                    .setSound("default")
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("메시지 전송 성공: {}", response);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            log.error("메시지 전송 실패", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("FCM 메시지 전송 실패", e);
        }
    }

    // 여러 기기에 메시지 전송
    public BatchResponse sendMessageToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        try {
            // UTF-8 인코딩 확인 및 보정
            String encodedTitle = ensureUTF8(title);
            String encodedBody = ensureUTF8(body);

            Map<String, String> fullData = new HashMap<>(data);
            fullData.put("title", encodedTitle);
            fullData.put("body", encodedBody);

            MulticastMessage message = MulticastMessage.builder()
                    .putAllData(fullData)
                    .addAllTokens(tokens)
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(encodedTitle)
                                            .setBody(encodedBody)
                                            .build())
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .build())
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            log.info("메시지 전송 성공: {}/{}", response.getSuccessCount(), tokens.size());
            return response;
        } catch (FirebaseMessagingException e) {
            log.error("다중 메시지 전송 실패", e);
            throw new RuntimeException("FCM 다중 메시지 전송 실패", e);
        }
    }

    // 특정 주제에 메시지 전송
    public String sendMessageToTopic(String topic, String title, String body, Map<String, String> data) {
        try {
            // UTF-8 인코딩 확인 및 보정
            String encodedTitle = ensureUTF8(title);
            String encodedBody = ensureUTF8(body);

            Map<String, String> fullData = new HashMap<>(data);
            fullData.put("title", encodedTitle);
            fullData.put("body", encodedBody);

            Message message = Message.builder()
                    .putAllData(fullData)
                    .setTopic(topic)
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(encodedTitle)
                                            .setBody(encodedBody)
                                            .build())
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("토픽 메시지 전송 성공: {}", response);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            log.error("토픽 메시지 전송 실패", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("FCM 토픽 메시지 전송 실패", e);
        }
    }

    /**
     * UTF-8 인코딩 확인 및 보정
     */
    private String ensureUTF8(String text) {
        if (text == null) {
            return "";
        }
        try {
            // UTF-8로 명시적 변환
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("UTF-8 인코딩 보정 실패: {}", e.getMessage());
            return text;
        }
    }
}