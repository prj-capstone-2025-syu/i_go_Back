package com.example.demo.service;

import com.example.demo.handler.NotificationWebSocketHandler;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FCMService {

    private final NotificationWebSocketHandler webSocketHandler;

    // 단일 기기에 메시지 전송 (FCM 또는 WebSocket)
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
                            .setTtl(60000) // 1분 (밀리초 단위)
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
                            .putHeader("apns-expiration", String.valueOf(System.currentTimeMillis() / 1000 + 60)) // 1분 후 만료 (초 단위 Unix timestamp)
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
                            .putHeader("TTL", "60") // 1분 (초 단위)
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
            log.error("FCM 메시지 전송 실패 - 오류: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 예외를 던지지 않고 null 반환하여 DB 저장이 롤백되지 않도록 함
            return null;
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
            log.error("다중 FCM 메시지 전송 실패 - 오류: {}", e.getMessage(), e);
            // 예외를 던지지 않고 null 반환
            return null;
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
            log.error("FCM 토픽 메시지 전송 실패 - 오류: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // 예외를 던지지 않고 null 반환
            return null;
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

    /**
     * 사용자에게 알림 전송 (FCM 우선, 실패 시 WebSocket)
     */
    public void sendNotificationToUser(String userId, String fcmToken, String title, String body, Map<String, String> data) {
        boolean fcmSent = false;

        // FCM 토큰이 있으면 FCM으로 전송 시도
        if (fcmToken != null && !fcmToken.isEmpty()) {
            try {
                sendMessageToToken(fcmToken, title, body, data);
                fcmSent = true;
                log.info("✅ [FCMService] FCM 알림 전송 성공: userId={}, title={}", userId, title);
            } catch (Exception e) {
                log.warn("⚠️ [FCMService] FCM 알림 전송 실패, WebSocket으로 재시도: userId={}, title={}, 오류: {}",
                        userId, title, e.getMessage());
            }
        } else {
            log.info("📭 [FCMService] FCM 토큰 없음, WebSocket으로 전송 시도: userId={}", userId);
        }

        // FCM 전송 실패하거나 토큰이 없으면 WebSocket으로 전송
        if (!fcmSent) {
            if (webSocketHandler.isUserConnected(userId)) {
                Map<String, String> wsData = new HashMap<>(data);
                wsData.put("title", title);
                wsData.put("body", body);

                log.info("📨 [FCMService → WebSocket] 메시지 전송 시작");
                log.info("   ├─ userId: {}", userId);
                log.info("   ├─ title: {}", title);
                log.info("   ├─ body: {}", body);
                log.info("   ├─ type: {}", data.get("type"));
                log.info("   └─ data: {}", wsData);

                webSocketHandler.sendNotificationToUser(userId, wsData);
                log.info("✅ [FCMService] WebSocket 알림 전송 성공: userId={}, title={}", userId, title);
            } else {
                log.warn("❌ [FCMService] FCM과 WebSocket 모두 사용 불가: userId={}, WebSocket 연결 안됨", userId);
            }
        }
    }
}
