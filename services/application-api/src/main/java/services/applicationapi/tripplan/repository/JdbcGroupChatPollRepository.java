package services.applicationapi.tripplan.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.tripplan.dto.GroupChatPollResponse;
import services.applicationapi.tripplan.service.exceptions.GroupChatConflictException;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;
import services.applicationapi.tripplan.service.exceptions.GroupChatPollNotFoundException;

@Repository
public class JdbcGroupChatPollRepository implements GroupChatPollRepository {

    private final JdbcTemplate jdbc;

    public JdbcGroupChatPollRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID messageId, List<String> labels) {
        jdbc.update("INSERT INTO trip_group_chat_polls (message_id) VALUES (?)", messageId);
        for (int position = 0; position < labels.size(); position++) {
            jdbc.update("""
                    INSERT INTO trip_group_chat_poll_options (id, poll_message_id, label, position)
                    VALUES (?, ?, ?, ?)
                    """, UUID.randomUUID(), messageId, labels.get(position), position);
        }
    }

    @Override
    public Optional<GroupChatPollResponse> snapshot(UUID messageId, UUID viewerId) {
        List<PollState> states = jdbc.query("""
                SELECT revision, closed_at FROM trip_group_chat_polls WHERE message_id = ?
                """, (rs, row) -> {
            OffsetDateTime closedAt = rs.getObject("closed_at", OffsetDateTime.class);
            return new PollState(rs.getLong("revision"),
                    closedAt == null ? null : closedAt.toInstant());
        }, messageId);
        if (states.isEmpty()) return Optional.empty();

        List<GroupChatPollResponse.Option> options = new ArrayList<>();
        UUID[] selected = new UUID[1];
        jdbc.query("""
                SELECT o.id, o.label, COUNT(v.user_id) AS votes,
                       BOOL_OR(v.user_id = ?) AS selected
                FROM trip_group_chat_poll_options o
                LEFT JOIN trip_group_chat_poll_votes v
                    ON v.poll_message_id = o.poll_message_id AND v.option_id = o.id
                WHERE o.poll_message_id = ?
                GROUP BY o.id, o.label, o.position
                ORDER BY o.position
                """, optionRow -> {
            UUID optionId = optionRow.getObject("id", UUID.class);
            options.add(new GroupChatPollResponse.Option(optionId,
                    optionRow.getString("label"), optionRow.getInt("votes")));
            if (optionRow.getBoolean("selected")) selected[0] = optionId;
        }, viewerId, messageId);
        PollState state = states.getFirst();
        return Optional.of(new GroupChatPollResponse(state.revision(),
                state.closedAt(), selected[0], options));
    }

    @Override
    public boolean vote(UUID messageId, UUID userId, UUID optionId, Instant now) {
        List<Boolean> closed = jdbc.query("""
                SELECT closed_at IS NOT NULL AS closed FROM trip_group_chat_polls
                WHERE message_id = ? FOR UPDATE
                """, (rs, row) -> rs.getBoolean("closed"), messageId);
        if (closed.isEmpty()) throw new GroupChatPollNotFoundException();

        Boolean validOption = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM trip_group_chat_poll_options
                    WHERE poll_message_id = ? AND id = ?)
                """, Boolean.class, messageId, optionId);
        if (!Boolean.TRUE.equals(validOption)) {
            throw new GroupChatInvalidMessageException("Select an option from this poll");
        }

        List<UUID> existing = jdbc.query("""
                SELECT option_id FROM trip_group_chat_poll_votes
                WHERE poll_message_id = ? AND user_id = ?
                """, (rs, row) -> rs.getObject("option_id", UUID.class), messageId, userId);
        if (!existing.isEmpty()) {
            if (existing.getFirst().equals(optionId)) return false;
            throw new GroupChatConflictException("You already voted in this poll");
        }
        if (closed.getFirst()) throw new GroupChatConflictException("This poll is closed");

        jdbc.update("""
                INSERT INTO trip_group_chat_poll_votes
                    (poll_message_id, user_id, option_id, created_at)
                VALUES (?, ?, ?, ?)
                """, messageId, userId, optionId, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        jdbc.update("UPDATE trip_group_chat_polls SET revision = revision + 1 WHERE message_id = ?", messageId);
        return true;
    }

    @Override
    public boolean close(UUID messageId, Instant now) {
        List<Boolean> closed = jdbc.query("""
                SELECT closed_at IS NOT NULL AS closed FROM trip_group_chat_polls
                WHERE message_id = ? FOR UPDATE
                """, (rs, row) -> rs.getBoolean("closed"), messageId);
        if (closed.isEmpty()) throw new GroupChatPollNotFoundException();
        if (closed.getFirst()) return false;

        jdbc.update("""
                UPDATE trip_group_chat_polls
                SET closed_at = ?, revision = revision + 1 WHERE message_id = ?
                """, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), messageId);
        return true;
    }

    private record PollState(long revision, Instant closedAt) {
    }
}
