package services.applicationapi.notifications.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.notifications.model.InboxNotification;
import services.applicationapi.notifications.model.NotificationDraft;
import services.applicationapi.notifications.model.NotificationTarget;
import services.applicationapi.notifications.model.PendingInboxSignal;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcNotificationInboxRepository implements NotificationInboxRepository {

    private static final TypeReference<Map<String, String>> PARAMS_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcNotificationInboxRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public boolean insert(NotificationDraft draft) {
        NotificationTarget target = draft.target();
        var params = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("userId", draft.recipientUserId())
                .addValue("source", draft.source())
                .addValue("eventId", draft.sourceEventId())
                .addValue("category", draft.category())
                .addValue("title", draft.title())
                .addValue("body", draft.body())
                .addValue("targetKey", target == null ? null : target.key())
                .addValue("targetParams", mapper.writeValueAsString(target == null ? Map.of() : target.params()));

        List<UUID> insertedIds = jdbc.query("""
                INSERT INTO notification_inbox
                    (id, recipient_user_id, source, source_event_id, category, title, body,
                     target_key, target_params, created_at)
                VALUES (:id, :userId, :source, :eventId, :category, :title, :body,
                        :targetKey, CAST(:targetParams AS jsonb), CURRENT_TIMESTAMP)
                ON CONFLICT (recipient_user_id, source, source_event_id) DO NOTHING
                RETURNING id
                """, params, (rs, rowNum) -> rs.getObject("id", UUID.class));

        if (insertedIds.isEmpty()) return false;
        jdbc.update("""
                INSERT INTO notification_inbox_outbox (notification_id)
                VALUES (:id)
                """, Map.of("id", insertedIds.getFirst()));
        return true;
    }

    @Override
    public List<InboxNotification> findPage(UUID userId, UUID before, int limit) {
        var params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("before", before)
                .addValue("limit", limit);

        return jdbc.query("""
                SELECT id, category, title, body, target_key, target_params, created_at, read_at
                FROM notification_inbox
                WHERE recipient_user_id = :userId
                  AND deleted_at IS NULL
                  AND (CAST(:before AS uuid) IS NULL OR (created_at, id) < (
                      SELECT created_at, id FROM notification_inbox
                      WHERE id = :before AND recipient_user_id = :userId
                  ))
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """, params, rowMapper());
    }

    @Override
    public long countUnread(UUID userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_inbox
                WHERE recipient_user_id = :userId AND read_at IS NULL AND deleted_at IS NULL
                """, Map.of("userId", userId), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public boolean markRead(UUID userId, UUID notificationId) {
        return jdbc.update("""
                UPDATE notification_inbox
                SET read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                WHERE id = :id AND recipient_user_id = :userId AND deleted_at IS NULL
                """, Map.of("id", notificationId, "userId", userId)) == 1;
    }

    @Override
    public boolean delete(UUID userId, UUID notificationId) {
        return jdbc.update("""
                UPDATE notification_inbox
                SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = :id AND recipient_user_id = :userId AND deleted_at IS NULL
                """, Map.of("id", notificationId, "userId", userId)) == 1;
    }

    @Override
    public List<PendingInboxSignal> lockPendingSignals(int limit) {
        return jdbc.query("""
                SELECT o.notification_id, n.recipient_user_id, o.attempts
                FROM notification_inbox_outbox o
                JOIN notification_inbox n ON n.id = o.notification_id
                WHERE o.published_at IS NULL AND o.next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY o.next_attempt_at, o.created_at, o.notification_id
                LIMIT :limit
                FOR UPDATE OF o SKIP LOCKED
                """, Map.of("limit", limit), (rs, rowNum) -> new PendingInboxSignal(
                rs.getObject("notification_id", UUID.class),
                rs.getObject("recipient_user_id", UUID.class),
                rs.getInt("attempts")));
    }

    @Override
    public void markSignalPublished(UUID notificationId) {
        jdbc.update("""
                UPDATE notification_inbox_outbox
                SET published_at = CURRENT_TIMESTAMP
                WHERE notification_id = :id
                """, Map.of("id", notificationId));
    }

    @Override
    public void deferSignal(UUID notificationId, Instant nextAttemptAt) {
        jdbc.update("""
                UPDATE notification_inbox_outbox
                SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt
                WHERE notification_id = :id
                """, Map.of("id", notificationId, "nextAttemptAt", nextAttemptAt));
    }

    private RowMapper<InboxNotification> rowMapper() {
        return (rs, rowNum) -> new InboxNotification(
                rs.getObject("id", UUID.class),
                rs.getString("category"),
                rs.getString("title"),
                rs.getString("body"),
                target(rs),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant());
    }

    private NotificationTarget target(ResultSet rs) throws SQLException {
        String key = rs.getString("target_key");
        return key == null ? null : new NotificationTarget(
                key, mapper.readValue(rs.getString("target_params"), PARAMS_TYPE));
    }
}
