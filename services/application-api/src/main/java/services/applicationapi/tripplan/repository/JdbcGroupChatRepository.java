package services.applicationapi.tripplan.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import services.applicationapi.tripplan.model.GroupChatMessage;

@Repository
public class JdbcGroupChatRepository implements GroupChatRepository {

    private static final String MESSAGE_SELECT = """
            SELECT m.id, m.conversation_id, m.sequence, m.sender_id, m.body, m.message_type, m.created_at,
                   COALESCE(NULLIF(u.username, ''), NULLIF(BTRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                            'Former member') AS sender_name
            FROM trip_group_chat_messages m
            LEFT JOIN users u ON u.id = m.sender_id
            """;

    private static final RowMapper<GroupChatMessage> MESSAGE_MAPPER = (rs, row) -> mapMessage(rs);

    private final JdbcTemplate jdbc;

    public JdbcGroupChatRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GroupChatMessage> findById(UUID messageId) {
        return jdbc.query(MESSAGE_SELECT + " WHERE m.id = ?", MESSAGE_MAPPER, messageId)
                .stream().findFirst();
    }

    @Override
    public GroupChatMessage insert(UUID messageId, UUID conversationId, UUID senderId,
                                   String body, String messageType, Instant createdAt) {
        jdbc.update("""
                INSERT INTO trip_group_chat_conversations (group_id)
                VALUES (?) ON CONFLICT (group_id) DO NOTHING
                """, conversationId);

        List<long[]> counters = jdbc.query("""
                SELECT next_sequence, next_event_sequence FROM trip_group_chat_conversations
                WHERE group_id = ? FOR UPDATE
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)}, conversationId);
        if (counters.isEmpty()) throw new IllegalStateException("Group conversation was not created");
        long next = counters.getFirst()[0];
        long eventSequence = counters.getFirst()[1];

        // The conversation lock serializes sequence allocation and duplicate retries.
        Optional<GroupChatMessage> existing = findById(messageId);
        if (existing.isPresent()) return existing.get();

        jdbc.update("""
                UPDATE trip_group_chat_conversations
                SET next_sequence = ?, next_event_sequence = ? WHERE group_id = ?
                """, next + 1, eventSequence + 1, conversationId);
        jdbc.update("""
                INSERT INTO trip_group_chat_messages
                    (id, conversation_id, sequence, sender_id, body, message_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, messageId, conversationId, next, senderId, body, messageType,
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        enqueueEvent(messageId, messageId, conversationId, next, eventSequence, null, createdAt);

        return findById(messageId).orElseThrow();
    }

    @Override
    public List<GroupChatMessage> latest(UUID conversationId, int limit) {
        List<GroupChatMessage> messages = new ArrayList<>(jdbc.query(MESSAGE_SELECT + """
                WHERE m.conversation_id = ? ORDER BY m.sequence DESC LIMIT ?
                """, MESSAGE_MAPPER, conversationId, limit));
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public List<GroupChatMessage> after(UUID conversationId, long sequence, int limit) {
        return jdbc.query(MESSAGE_SELECT + """
                WHERE m.conversation_id = ? AND m.sequence > ?
                ORDER BY m.sequence ASC LIMIT ?
                """, MESSAGE_MAPPER, conversationId, sequence, limit);
    }

    @Override
    public List<GroupChatMessage> before(UUID conversationId, long sequence, int limit) {
        List<GroupChatMessage> messages = new ArrayList<>(jdbc.query(MESSAGE_SELECT + """
                WHERE m.conversation_id = ? AND m.sequence < ?
                ORDER BY m.sequence DESC LIMIT ?
                """, MESSAGE_MAPPER, conversationId, sequence, limit));
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public long eventCursor(UUID conversationId) {
        Long current = jdbc.queryForObject("""
                SELECT COALESCE(MAX(event_sequence), 0) FROM trip_group_chat_outbox
                WHERE conversation_id = ?
                """, Long.class, conversationId);
        return current == null ? 0 : current;
    }

    @Override
    public long eventSequence(UUID eventId) {
        Long sequence = jdbc.queryForObject("""
                SELECT event_sequence FROM trip_group_chat_outbox WHERE event_id = ?
                """, Long.class, eventId);
        if (sequence == null) throw new IllegalStateException("Chat event was not found");
        return sequence;
    }

    @Override
    public List<EventReference> eventsAfter(UUID conversationId, long eventSequence, int limit) {
        return jdbc.query("""
                SELECT event_sequence, message_id FROM trip_group_chat_outbox
                WHERE conversation_id = ? AND event_sequence > ?
                ORDER BY event_sequence LIMIT ?
                """, (rs, row) -> new EventReference(
                rs.getLong("event_sequence"), rs.getObject("message_id", UUID.class)),
                conversationId, eventSequence, limit);
    }

    @Override
    public long nextEventSequence(UUID conversationId) {
        Long next = jdbc.queryForObject("""
                SELECT next_event_sequence FROM trip_group_chat_conversations
                WHERE group_id = ? FOR UPDATE
                """, Long.class, conversationId);
        if (next == null) throw new IllegalStateException("Group conversation was not created");
        jdbc.update("""
                UPDATE trip_group_chat_conversations SET next_event_sequence = ? WHERE group_id = ?
                """, next + 1, conversationId);
        return next;
    }

    @Override
    public void enqueueEvent(UUID eventId, UUID messageId, UUID conversationId, long sequence,
                             long eventSequence, String payload, Instant createdAt) {
        jdbc.update("""
                INSERT INTO trip_group_chat_outbox
                    (event_id, message_id, conversation_id, sequence, event_sequence, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, eventId, messageId, conversationId, sequence, eventSequence, payload,
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    @Override
    public void setPayload(UUID eventId, String payload) {
        jdbc.update("UPDATE trip_group_chat_outbox SET payload = ? WHERE event_id = ?", payload, eventId);
    }

    @Override
    public List<PendingEvent> lockPending(int limit) {
        return jdbc.query("""
                SELECT m.id, m.conversation_id, m.sequence, m.sender_id, m.body, m.message_type, m.created_at,
                       COALESCE(NULLIF(u.username, ''), NULLIF(BTRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                'Former member') AS sender_name,
                       o.event_id, o.event_sequence, o.payload, o.attempts
                FROM trip_group_chat_outbox o
                JOIN trip_group_chat_messages m ON m.id = o.message_id
                LEFT JOIN users u ON u.id = m.sender_id
                WHERE o.published_at IS NULL AND o.next_attempt_at <= now()
                  AND NOT EXISTS (
                      SELECT 1 FROM trip_group_chat_outbox earlier
                      WHERE earlier.conversation_id = o.conversation_id
                        AND earlier.event_sequence < o.event_sequence AND earlier.published_at IS NULL
                  )
                ORDER BY o.created_at, o.event_id
                LIMIT ? FOR UPDATE OF o SKIP LOCKED
                """, (rs, row) -> new PendingEvent(mapMessage(rs),
                rs.getObject("event_id", UUID.class), rs.getLong("event_sequence"),
                rs.getString("payload"), rs.getInt("attempts")), limit);
    }

    @Override
    public void markPublished(UUID eventId) {
        jdbc.update("UPDATE trip_group_chat_outbox SET published_at = now() WHERE event_id = ?", eventId);
    }

    @Override
    public void defer(UUID eventId, Instant nextAttemptAt) {
        jdbc.update("""
                UPDATE trip_group_chat_outbox
                SET attempts = attempts + 1, next_attempt_at = ?
                WHERE event_id = ?
                """, OffsetDateTime.ofInstant(nextAttemptAt, ZoneOffset.UTC), eventId);
    }

    private static GroupChatMessage mapMessage(ResultSet rs) throws SQLException {
        return new GroupChatMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                rs.getLong("sequence"),
                rs.getObject("sender_id", UUID.class),
                rs.getString("sender_name"),
                rs.getString("body"),
                rs.getString("message_type"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
