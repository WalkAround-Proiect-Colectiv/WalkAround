package services.applicationapi.poicomments.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.poicomments.service.PoiCommentService.Comment;
import services.applicationapi.poicomments.service.PoiCommentService.Cursor;
import services.applicationapi.poicomments.service.PoiCommentService.Report;

@Repository
public class PoiCommentRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JdbcTemplate plain;

    public PoiCommentRepository(NamedParameterJdbcTemplate jdbc, JdbcTemplate plain) {
        this.jdbc = jdbc;
        this.plain = plain;
    }

    public UUID subject(List<String> references) {
        plain.execute("SELECT pg_advisory_xact_lock(627514303)");
        var params = new MapSqlParameterSource("references", references);
        List<UUID> existing = jdbc.queryForList("""
                SELECT DISTINCT subject_id FROM poi_comment_aliases
                WHERE reference IN (:references) ORDER BY subject_id
                """, params, UUID.class);
        UUID subject = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        if (existing.isEmpty()) {
            plain.update("INSERT INTO poi_comment_subjects(id) VALUES (?)", subject);
        } else if (existing.size() > 1) {
            params.addValue("subjects", existing).addValue("subject", subject);
            jdbc.update("UPDATE poi_comments SET subject_id = :subject WHERE subject_id IN (:subjects)", params);
            jdbc.update("UPDATE poi_comment_aliases SET subject_id = :subject WHERE subject_id IN (:subjects)", params);
            jdbc.update("DELETE FROM poi_comment_subjects WHERE id IN (:subjects) AND id <> :subject", params);
        }
        for (String reference : references) {
            plain.update("""
                    INSERT INTO poi_comment_aliases(reference, subject_id) VALUES (?, ?)
                    ON CONFLICT (reference) DO NOTHING
                    """, reference, subject);
        }
        return subject;
    }

    public List<Comment> list(UUID subject, Cursor cursor) {
        var params = new MapSqlParameterSource("subject", subject);
        String page = "";
        if (cursor != null) {
            params.addValue("time", java.sql.Timestamp.from(cursor.time())).addValue("id", cursor.id());
            page = " AND (comment.created_at, comment.id) < (:time, :id)";
        }
        return jdbc.query("""
                SELECT comment.*, coalesce(nullif(author.username, ''), 'WalkAround user') AS author_name
                FROM poi_comments comment JOIN users author ON author.id = comment.author_id
                WHERE comment.subject_id = :subject
                """ + page + " ORDER BY comment.created_at DESC, comment.id DESC LIMIT 21", params,
                (result, row) -> comment(result));
    }

    public Comment find(UUID id) {
        return plain.query("""
                SELECT comment.*, coalesce(nullif(author.username, ''), 'WalkAround user') AS author_name
                FROM poi_comments comment JOIN users author ON author.id = comment.author_id
                WHERE comment.id = ? FOR UPDATE OF comment
                """, (result, row) -> comment(result), id).stream().findFirst().orElse(null);
    }

    public boolean userExists(UUID user) {
        return Boolean.TRUE.equals(plain.queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)", Boolean.class, user));
    }

    public boolean allowWrite(UUID user) {
        return !plain.query("""
                INSERT INTO poi_comment_rate_limits(user_id, window_start, action_count) VALUES (?, now(), 1)
                ON CONFLICT (user_id) DO UPDATE SET
                    window_start = CASE WHEN poi_comment_rate_limits.window_start <= now() - interval '1 minute'
                        THEN now() ELSE poi_comment_rate_limits.window_start END,
                    action_count = CASE WHEN poi_comment_rate_limits.window_start <= now() - interval '1 minute'
                        THEN 1 ELSE poi_comment_rate_limits.action_count + 1 END
                WHERE poi_comment_rate_limits.window_start <= now() - interval '1 minute'
                    OR poi_comment_rate_limits.action_count < 10
                RETURNING user_id
                """, (result, row) -> result.getObject(1, UUID.class), user).isEmpty();
    }

    public Comment create(UUID subject, UUID author, String body) {
        UUID id = UUID.randomUUID();
        plain.update("INSERT INTO poi_comments(id, subject_id, author_id, body) VALUES (?, ?, ?, ?)", id, subject, author, body);
        return find(id);
    }

    public Comment edit(UUID id, String body) {
        plain.update("UPDATE poi_comments SET body = ?, updated_at = clock_timestamp() WHERE id = ?", body, id);
        return find(id);
    }

    public void delete(UUID id) {
        plain.update("DELETE FROM poi_comments WHERE id = ?", id);
    }

    public void report(UUID id, UUID reporter, String reason) {
        plain.update("""
                INSERT INTO poi_comment_reports(id, comment_id, reporter_id, reason) VALUES (?, ?, ?, ?)
                ON CONFLICT (comment_id, reporter_id) DO NOTHING
                """, UUID.randomUUID(), id, reporter, reason);
    }

    public List<Report> reports(Cursor cursor) {
        var params = new MapSqlParameterSource();
        String page = "";
        if (cursor != null) {
            params.addValue("time", java.sql.Timestamp.from(cursor.time())).addValue("id", cursor.id());
            page = " WHERE (report.created_at, report.id) < (:time, :id)";
        }
        return jdbc.query("""
                SELECT report.id, report.comment_id, report.reason, report.created_at, comment.body
                FROM poi_comment_reports report JOIN poi_comments comment ON comment.id = report.comment_id
                """ + page + " ORDER BY report.created_at DESC, report.id DESC LIMIT 21", params,
                (result, row) -> new Report(result.getObject("id", UUID.class), result.getObject("comment_id", UUID.class),
                        result.getString("reason"), result.getString("body"), result.getTimestamp("created_at").toInstant()));
    }

    private Comment comment(ResultSet result) throws SQLException {
        return new Comment(result.getObject("id", UUID.class), result.getObject("author_id", UUID.class),
                result.getString("author_name"), result.getString("body"),
                result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());
    }
}