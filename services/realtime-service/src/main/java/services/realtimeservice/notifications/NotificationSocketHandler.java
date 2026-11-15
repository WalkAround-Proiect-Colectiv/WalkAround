package services.realtimeservice.notifications;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-user notifications. Each connection belongs to the signed-in user who opened it; messages from the
 * reminder topic go only to the connections of the user named in the message, unlike the dangerous-zone
 * broadcast which goes to everyone.
 */
@Component
public class NotificationSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationSocketHandler.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 5000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;

    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public NotificationSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = userId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES);
        decorated.put(session.getId(), safe);
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(safe);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        WebSocketSession safe = decorated.remove(session.getId());
        String userId = userId(session);
        if (safe == null || userId == null) {
            return;
        }
        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(safe);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @KafkaListener(topics = "${app.notifications.reminder-topic}", groupId = "${CONSUMER_GROUP_ID}-notifications")
    public void consumeReminder(String message) {
        String userId;
        try {
            JsonNode node = objectMapper.readTree(message);
            userId = node.path("userId").asString(null);
        } catch (JacksonException exception) {
            log.warn("Ignoring malformed reminder message");
            return;
        }
        if (userId == null) {
            return;
        }
        deliver(userId, message);
    }

    /** Sends a message to every open connection of one user. Returns the number of connections reached. */
    int deliver(String userId, String message) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return 0;
        }
        int delivered = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(message));
                delivered++;
            } catch (IOException | RuntimeException exception) {
                log.warn("Could not deliver a notification: {}", exception.getMessage());
            }
        }
        return delivered;
    }

    private static String userId(WebSocketSession session) {
        Object value = session.getAttributes().get(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return value instanceof String id && !id.isBlank() ? id : null;
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // The connection is being discarded anyway.
        }
    }
}
