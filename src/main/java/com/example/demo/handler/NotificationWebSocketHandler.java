package com.example.demo.handler;

import com.example.demo.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    // userId -> WebSocketSession 매핑 (한 사용자가 여러 세션을 가질 수 있음)
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 쿼리 파라미터에서 토큰 추출
        String query = session.getUri().getQuery();
        String token = extractTokenFromQuery(query);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String userEmail = jwtTokenProvider.getUserId(token); // 실제로는 이메일이 반환됨
            userSessions.put(userEmail, session);
            log.info("✅ [WebSocket] 연결 성공! userEmail: {}, sessionId: {}, 총 연결 수: {}",
                userEmail, session.getId(), userSessions.size());

            // 연결 성공 메시지 전송
            sendMessage(session, Map.of(
                "type", "CONNECTION_SUCCESS",
                "message", "WebSocket 연결이 성공했습니다."
            ));
        } else {
            log.warn("⚠️ [WebSocket] 유효하지 않은 토큰으로 연결 시도 실패");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 클라이언트로부터 메시지를 받았을 때 처리 (필요시 구현)
        log.debug("📨 [WebSocket] 클라이언트 메시지 수신: {}", message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 연결 종료 시 세션 제거
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            userSessions.remove(userId);
            log.info("🔌 [WebSocket] 연결 종료 - userId: {}, sessionId: {}, 총 연결 수: {}",
                userId, session.getId(), userSessions.size());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = getUserIdFromSession(session);

        // Connection reset 에러는 클라이언트가 연결을 끊은 것이므로 경고 수준으로 처리
        if (exception.getMessage() != null && exception.getMessage().contains("Connection reset")) {
            log.warn("⚠️ [WebSocket] 클라이언트 연결 끊김 - userId: {}, sessionId: {}",
                userId != null ? userId : "unknown", session.getId());
        } else {
            log.error("❌ [WebSocket] 전송 오류 - userId: {}, sessionId: {}, 오류: {}",
                userId != null ? userId : "unknown", session.getId(), exception.getMessage());
        }

        if (userId != null) {
            userSessions.remove(userId);
        }

        // 세션이 이미 닫혀있을 수 있으므로 체크 후 닫기
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception e) {
            // 세션 닫기 실패는 무시 (이미 닫혀있을 수 있음)
            log.debug("세션 닫기 실패 (이미 닫혀있을 수 있음): {}", e.getMessage());
        }
    }

    /**
     * 특정 사용자에게 알림 전송
     */
    public void sendNotificationToUser(String userId, Map<String, String> notificationData) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> message = Map.of(
                    "type", notificationData.getOrDefault("type", "NOTIFICATION"),
                    "title", notificationData.getOrDefault("title", ""),
                    "body", notificationData.getOrDefault("body", ""),
                    "data", notificationData
                );

                log.info("📤 [WebSocket] 메시지 전송 중...");
                log.info("   ├─ userId: {}", userId);
                log.info("   ├─ sessionId: {}", session.getId());
                log.info("   ├─ type: {}", message.get("type"));
                log.info("   ├─ title: {}", message.get("title"));
                log.info("   ├─ body: {}", message.get("body"));
                log.info("   └─ 전체 메시지: {}", message);

                sendMessage(session, message);
                log.info("✅ [WebSocket] 메시지 전송 완료: userId={}", userId);
            } catch (Exception e) {
                log.error("❌ [WebSocket] 메시지 전송 실패: userId={}, 오류: {}", userId, e.getMessage(), e);
            }
        } else {
            log.debug("⚠️ [WebSocket] 세션이 없거나 닫혀있음: userId={}", userId);
        }
    }

    /**
     * 세션에 메시지 전송
     */
    private void sendMessage(WebSocketSession session, Map<String, ?> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("메시지 전송 실패: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 쿼리 파라미터에서 토큰 추출
     */
    private String extractTokenFromQuery(String query) {
        if (query == null) return null;
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && "token".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    /**
     * 세션에서 userId 조회
     */
    private String getUserIdFromSession(WebSocketSession session) {
        return userSessions.entrySet().stream()
            .filter(entry -> entry.getValue().getId().equals(session.getId()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }

    /**
     * 활성 WebSocket 연결 수 반환
     */
    public int getActiveConnectionCount() {
        return userSessions.size();
    }

    /**
     * 특정 사용자의 WebSocket 연결 여부 확인
     */
    public boolean isUserConnected(String userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }
}