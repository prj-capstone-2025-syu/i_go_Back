package com.example.demo.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class FCMService {

    // 단일 기기에 메시지 전송
    public String sendMessageToToken(String token, String title, String body, Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .setToken(token)
                    .build();

            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("메시지 전송 성공: {}", response);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            log.error("메시지 전송 실패", e);
            throw new RuntimeException("FCM 메시지 전송 실패", e);
        }
    }

    // 여러 기기에 메시지 전송
    public BatchResponse sendMessageToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        try {
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .addAllTokens(tokens)
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
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .setTopic(topic)
                    .build();

            String response = FirebaseMessaging.getInstance().sendAsync(message).get();
            log.info("토픽 메시지 전송 성공: {}", response);
            return response;
        } catch (ExecutionException | InterruptedException e) {
            log.error("토픽 메시지 전송 실패", e);
            throw new RuntimeException("FCM 토픽 메시지 전송 실패", e);
        }
    }
}