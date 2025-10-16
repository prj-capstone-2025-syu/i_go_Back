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

            // data 페이로드에는 커스텀 데이터만 포함 (title, body 제외하여 중복 알림 방지)
            Map<String, String> dataOnly = new HashMap<>(data);

            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(encodedTitle)
                            .setBody(encodedBody)
                            .build())
                    .putAllData(dataOnly)
                    .setToken(token)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .setIcon("ic_notification")
                                    .setColor("#FF6B35")
                                    .setSound("default")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build())
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(encodedTitle)
                                            .setBody(encodedBody)
                                            .build())
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .setIcon("/logo.png")
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("FCM 메시지 전송 성공: {}", response);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            log.error("FCM 메시지 전송 실패", e);
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

            Map<String, String> dataOnly = new HashMap<>(data);

            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(encodedTitle)
                            .setBody(encodedBody)
                            .build())
                    .putAllData(dataOnly)
                    .addAllTokens(tokens)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .setIcon("ic_notification")
                                    .setColor("#FF6B35")
                                    .setSound("default")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build())
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(encodedTitle)
                                            .setBody(encodedBody)
                                            .build())
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .setIcon("/logo.png")
                                    .build())
                            .build())
                    .build();

            BatchResponse response = FirebaseMessaging.getInstance().sendMulticast(message);
            log.info("다중 FCM 메시지 전송 성공: {}/{}", response.getSuccessCount(), tokens.size());
            return response;
        } catch (FirebaseMessagingException e) {
            log.error("다중 FCM 메시지 전송 실패", e);
            throw new RuntimeException("FCM 다중 메시지 전송 실패", e);
        }
    }

    // 특정 주제에 메시지 전송
    public String sendMessageToTopic(String topic, String title, String body, Map<String, String> data) {
        try {
            // UTF-8 인코딩 확인 및 보정
            String encodedTitle = ensureUTF8(title);
            String encodedBody = ensureUTF8(body);

            Map<String, String> dataOnly = new HashMap<>(data);

            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(encodedTitle)
                            .setBody(encodedBody)
                            .build())
                    .putAllData(dataOnly)
                    .setTopic(topic)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .setIcon("ic_notification")
                                    .setColor("#FF6B35")
                                    .setSound("default")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build())
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .putHeader("apns-priority", "10")
                            .setAps(Aps.builder()
                                    .setAlert(ApsAlert.builder()
                                            .setTitle(encodedTitle)
                                            .setBody(encodedBody)
                                            .build())
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .setWebpushConfig(WebpushConfig.builder()
                            .putHeader("Urgency", "high")
                            .setNotification(WebpushNotification.builder()
                                    .setTitle(encodedTitle)
                                    .setBody(encodedBody)
                                    .setIcon("/logo.png")
                                    .build())
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("FCM 토픽 메시지 전송 성공: {}", response);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            log.error("FCM 토픽 메시지 전송 실패", e);
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

