package services.realtimeservice.chat;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

@Component
public class GroupChatSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupChatSocketHandler.class);

    private final Map<UUID, Set<WebSocketSession>> sessionsByConversation = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> decorated = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSequenceBySession = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public GroupChatSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID conversationId = conversationId(session);
        if (conversationId == null || expired(session)) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);
        decorated.put(session.getId(), safe);
        sessionsByConversation.computeIfAbsent(conversationId, ignored -> ConcurrentHashMap.newKeySet()).add(safe);
        try {
            safe.sendMessage(new TextMessage(mapper.writeValueAsString(
                    new SubscriptionAck("SUBSCRIBED", conversationId))));
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not acknowledge group chat subscription {}", conversationId);
            remove(session);
            closeQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Chat writes are accepted only through the authenticated HTTP endpoint.
        closeQuietly(session, CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        remove(session);
    }

    @KafkaListener(topics = "${app.group-chat.topic}",
            groupId = "${CONSUMER_GROUP_ID}-group-chat-${random.uuid}",
            properties = "auto.offset.reset:latest")
    public void consume(String payload) {
        try {
            JsonNode event = mapper.readTree(payload);
            if (!"GROUP_CHAT_MESSAGE".equals(event.path("type").asString())) return;
            UUID conversationId = UUID.fromString(event.path("conversationId").asString());
            UUID.fromString(event.path("messageId").asString());
            long sequence = event.path("sequence").asLong(0);
            if (sequence < 1 || !event.path("body").isTextual()) return;
            int delivered = deliver(conversationId, sequence, payload);
            log.info("Received group chat event for conversation {} at sequence {}; delivered to {} connections",
                    conversationId, sequence, delivered);
        } catch (JacksonException | IllegalArgumentException exception) {
            log.warn("Ignoring malformed group chat event");
        }
    }

    private int deliver(UUID conversationId, long sequence, String payload) {
        Set<WebSocketSession> sessions = sessionsByConversation.get(conversationId);
        if (sessions == null) return 0;
        int delivered = 0;
        for (WebSocketSession session : sessions) {
            if (expired(session) || !session.isOpen()) {
                remove(session);
                closeQuietly(session, CloseStatus.POLICY_VIOLATION);
                continue;
            }
            Long last = lastSequenceBySession.get(session.getId());
            if (last != null && sequence <= last) continue;
            try {
                session.sendMessage(new TextMessage(payload));
                lastSequenceBySession.put(session.getId(), sequence);
                delivered++;
            } catch (IOException | RuntimeException exception) {
                log.warn("Could not deliver group chat message to connection {}", session.getId());
                remove(session);
                closeQuietly(session, CloseStatus.SERVER_ERROR);
            }
        }
        return delivered;
    }

    private void remove(WebSocketSession session) {
        WebSocketSession safe = decorated.remove(session.getId());
        lastSequenceBySession.remove(session.getId());
        UUID conversationId = conversationId(session);
        if (safe == null || conversationId == null) return;
        sessionsByConversation.computeIfPresent(conversationId, (key, sessions) -> {
            sessions.remove(safe);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private static UUID conversationId(WebSocketSession session) {
        Object value = session.getAttributes().get(GroupChatHandshakeInterceptor.CONVERSATION_ID);
        return value instanceof UUID id ? id : null;
    }

    private static boolean expired(WebSocketSession session) {
        Object value = session.getAttributes().get(GroupChatHandshakeInterceptor.EXPIRES_AT);
        return !(value instanceof Instant expiry) || !Instant.now().isBefore(expiry);
    }

    private static void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // The connection is already being discarded.
        }
    }

    private record SubscriptionAck(String type, UUID conversationId) {
    }
}
