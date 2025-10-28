package com.example.demo.service;

import com.example.demo.entity.user.User;
import com.example.demo.handler.NotificationWebSocketHandler;
import com.example.demo.repository.UserRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;

    // 단일 기기에 메시지 전송 (FCM)
    public String sendMessageToToken(String token, String title, String body, Map<String, String> data)
            throws FirebaseMessagingException, ExecutionException, InterruptedException {

        String encodedTitle = ensureUTF8(title);
        String encodedBody = ensureUTF8(body);
        Map<String, String> dataOnly = new HashMap<>(data);

        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(encodedTitle)
                        .setBody(encodedBody)
                        .build())
                .putAllData(dataOnly)
                .setToken(token)
                .setAndroidConfig(AndroidConfig.builder()
                        .setTtl(60000)
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
                        .putHeader("apns-expiration", String.valueOf(System.currentTimeMillis() / 1000 + 60))
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
                        .putHeader("TTL", "60")
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
    }

    /**
     * UTF-8 인코딩 확인 및 보정
     */
    private String ensureUTF8(String text) {
        if (text == null) {
            return "";
        }
        try {
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

            } catch (ExecutionException e) {
                log.warn("⚠️ [FCMService] FCM 알림 전송 ExecutionException: userId={}, title={}, 오류: {}",
                        userId, title, e.getMessage());

                if (e.getCause() instanceof FirebaseMessagingException fme) {
                    MessagingErrorCode errorCode = fme.getMessagingErrorCode();
                    log.warn("FirebaseMessagingException ErrorCode: {}", errorCode);

                    if (errorCode == MessagingErrorCode.UNREGISTERED ||
                        errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                        log.warn("!!! FCM 토큰 무효화 감지 ({}), DB에서 삭제합니다.", errorCode);
                        deleteInvalidFcmToken(fcmToken);
                    }
                }

            } catch (InterruptedException e) {
                log.warn("⚠️ [FCMService] FCM 알림 전송 InterruptedException: userId={}, title={}", userId, title);
                Thread.currentThread().interrupt();

            } catch (FirebaseMessagingException e) {
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                log.warn("⚠️ [FCMService] FCM 알림 전송 FirebaseMessagingException: userId={}, title={}, ErrorCode={}",
                        userId, title, errorCode);

                if (errorCode == MessagingErrorCode.UNREGISTERED ||
                    errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    log.warn("!!! FCM 토큰 무효화 감지 ({}), DB에서 삭제합니다.", errorCode);
                    deleteInvalidFcmToken(fcmToken);
                }

            } catch (Exception e) {
                log.warn("⚠️ [FCMService] FCM 알림 전송 중 예상치 못한 오류: userId={}, title={}, 오류: {}",
                        userId, title, e.getMessage());
            }
        } else {
            log.info("📭 [FCMService] FCM 토큰 없음, WebSocket으로 전송 시도: userId={}", userId);
        }

        // ⭐⭐⭐ FCM 전송 실패했거나 토큰이 없으면 항상 WebSocket 전송 시도
        if (!fcmSent) {
            log.info("📡 [FCMService] FCM 실패/없음, WebSocket 전송 시도: userId={}", userId);

            try {
                // WebSocket 데이터 준비
                Map<String, String> wsData = new HashMap<>(data);
                wsData.put("title", title);
                wsData.put("body", body);

                String type = data.get("type");
                log.debug("📨 [FCMService → WebSocket] 메시지 전송 시작: userId={}, type={}", userId, type);

                // ⭐ NotificationWebSocketHandler를 통해 전송
                webSocketHandler.sendNotificationToUser(userId, wsData);

                log.info("✅ [FCMService] WebSocket 알림 전송 성공: userId={}, title={}", userId, title);

            } catch (Exception e) {
                // WebSocket 전송 실패 (사용자 오프라인 가능)
                log.debug("⚠️ [FCMService] WebSocket 전송 실패 (사용자 오프라인 가능성): userId={}, 오류: {}",
                        userId, e.getMessage());
            }
        }
    }

    /**
     * 유효하지 않은 FCM 토큰을 DB에서 삭제
     */
    @Transactional
    public void deleteInvalidFcmToken(String invalidToken) {
        if (invalidToken == null || invalidToken.isEmpty()) {
            log.warn("삭제할 FCM 토큰 값이 비어있습니다.");
            return;
        }

        try {
            List<User> usersWithToken = userRepository.findAllByFcmToken(invalidToken);

            if (usersWithToken.isEmpty()) {
                log.warn("유효하지 않은 토큰 ({})에 해당하는 사용자를 DB에서 찾지 못했습니다.",
                        invalidToken.substring(0, Math.min(invalidToken.length(), 10)) + "...");
            } else {
                log.info("유효하지 않은 토큰 ({}) 삭제 대상 사용자 {}명 찾음",
                        invalidToken.substring(0, Math.min(invalidToken.length(), 10)) + "...",
                        usersWithToken.size());

                for (User user : usersWithToken) {
                    if (invalidToken.equals(user.getFcmToken())) {
                        log.info(" -> 사용자 ID {}의 FCM 토큰을 null로 업데이트합니다.", user.getId());
                        user.setFcmToken(null);
                        userRepository.save(user);
                    } else {
                        log.info(" -> 사용자 ID {}의 토큰이 이미 변경되어 삭제를 건너뜁니다.", user.getId());
                    }
                }

                log.info("유효하지 않은 토큰({})을 가진 사용자들의 토큰 삭제 처리 완료.",
                        invalidToken.substring(0, Math.min(invalidToken.length(), 10)) + "...");
            }
        } catch (Exception e) {
            log.error("DB에서 유효하지 않은 FCM 토큰 삭제 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}
