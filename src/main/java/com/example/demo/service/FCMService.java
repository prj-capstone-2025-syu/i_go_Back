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
import java.util.ArrayList;
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
                                .setIcon("ic_stat_name")
                                .setColor("#0078D4")
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
     * 사용자에게 알림 전송 (웹/앱 둘 다 시도)
     *
     * @param userId 알림 받을 사용자 ID (이걸로 유저 찾을 거야♡)
     * @param title  알림 제목
     * @param body   알림 내용
     * @param data   알림 데이터
     */
    public void sendNotificationToUser(String userId, String title, String body, Map<String, String> data) {
        long id = Long.parseLong(userId);
        User user = userRepository.findById(id).orElse(null);

        if (user == null) {
            log.warn("⚠️ [FCMService] 알림을 보낼 사용자를 찾을 수 없음: userId={}", userId);
            return;
        }

        String webFcmToken = user.getFcmToken();
        String appFcmToken = user.getAppFcmToken();

        boolean fcmSent = false; // 웹이든 앱이든 하나라도 성공하면 true
        boolean webTokenInvalid = false;
        boolean appTokenInvalid = false;

        // 1. 웹 토큰으로 전송 시도
        if (webFcmToken != null && !webFcmToken.isEmpty()) {
            try {
                sendMessageToToken(webFcmToken, title, body, data);
                fcmSent = true;
                log.info("✅ [FCMService] '웹' FCM 알림 전송 성공: userId={}, title={}", userId, title);
            } catch (Exception e) {
                log.warn("⚠️ [FCMService] '웹' FCM 알림 전송 실패: userId={}, title={}", userId, title);
                // 토큰이 무효한지 검사
                if (isTokenInvalidException(e)) {
                    webTokenInvalid = true;
                }
            }
        }

        // 2. 앱 토큰으로 전송 시도
        if (appFcmToken != null && !appFcmToken.isEmpty()) {
            try {
                sendMessageToToken(appFcmToken, title, body, data);
                fcmSent = true;
                log.info("✅ [FCMService] '앱' FCM 알림 전송 성공: userId={}, title={}", userId, title);
            } catch (Exception e) {
                log.warn("⚠️ [FCMService] '앱' FCM 알림 전송 실패: userId={}, title={}", userId, title);
                // 토큰이 무효한지 검사♡
                if (isTokenInvalidException(e)) {
                    appTokenInvalid = true;
                }
            }
        }

        if (webTokenInvalid) {
            deleteInvalidFcmToken(webFcmToken, "WEB");
        }
        if (appTokenInvalid) {
            deleteInvalidFcmToken(appFcmToken, "APP");
        }

        if (!fcmSent) {
            log.info("📡 [FCMService] FCM 실패/없음, WebSocket 전송 시도: userId={}", userId);
            try {
                Map<String, String> wsData = new HashMap<>(data);
                wsData.put("title", title);
                wsData.put("body", body);
                webSocketHandler.sendNotificationToUser(userId, wsData);
                log.info("✅ [FCMService] WebSocket 알림 전송 성공: userId={}, title={}", userId, title);
            } catch (Exception e) {
                log.debug("⚠️ [FCMService] WebSocket 전송 실패 (오프라인 추정): userId={}, 오류: {}",
                        userId, e.getMessage());
            }
        } else {
            log.info("📭 [FCMService] FCM 전송 성공, WebSocket은 생략: userId={}", userId);
        }
    }

    private boolean isTokenInvalidException(Exception e) {
        if (e.getCause() instanceof FirebaseMessagingException fme) {
            MessagingErrorCode errorCode = fme.getMessagingErrorCode();
            return errorCode == MessagingErrorCode.UNREGISTERED ||
                   errorCode == MessagingErrorCode.INVALID_ARGUMENT;
        }
        if (e instanceof FirebaseMessagingException fme) {
            MessagingErrorCode errorCode = fme.getMessagingErrorCode();
            return errorCode == MessagingErrorCode.UNREGISTERED ||
                   errorCode == MessagingErrorCode.INVALID_ARGUMENT;
        }
        return false;
    }

    @Transactional
    public void deleteInvalidFcmToken(String invalidToken, String tokenType) {
        if (invalidToken == null || invalidToken.isEmpty()) {
            log.warn("삭제할 FCM 토큰 값이 비어있습니다. (Type: {})", tokenType);
            return;
        }

        log.warn("!!! FCM 토큰 무효화 감지 (Type: {}), DB에서 삭제합니다. Token: {}...",
                tokenType, invalidToken.substring(0, Math.min(invalidToken.length(), 10)));

        try {
            List<User> usersWithToken;
            if ("APP".equals(tokenType)) {
                usersWithToken = userRepository.findAllByAppFcmToken(invalidToken);
            } else {
                // 기본은 웹 토큰(fcmToken) 검색
                usersWithToken = userRepository.findAllByFcmToken(invalidToken);
            }

            if (usersWithToken.isEmpty()) {
                log.warn("유효하지 않은 토큰 ({})에 해당하는 사용자를 DB에서 찾지 못했습니다.",
                        invalidToken.substring(0, Math.min(invalidToken.length(), 10)) + "...");
            } else {
                for (User user : usersWithToken) {
                    if ("APP".equals(tokenType) && invalidToken.equals(user.getAppFcmToken())) {
                        log.info(" -> 사용자 ID {}의 '앱' 토큰을 null로 업데이트합니다.", user.getId());
                        user.setAppFcmToken(null);
                        userRepository.save(user);
                    } else if ("WEB".equals(tokenType) && invalidToken.equals(user.getFcmToken())) {
                        log.info(" -> 사용자 ID {}의 '웹' 토큰을 null로 업데이트합니다.", user.getId());
                        user.setFcmToken(null);
                        userRepository.save(user);
                    }
                }
                log.info("유효하지 않은 토큰(Type: {}) 삭제 처리 완료.", tokenType);
            }
        } catch (Exception e) {
            log.error("DB에서 유효하지 않은 FCM 토큰 삭제 중 오류 발생: {}", e.getMessage(), e);
        }
    }
}