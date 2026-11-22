package services.applicationapi.poicomments.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.poicomments.repository.PoiCommentRepository;
import services.applicationapi.recommendation.repository.PoiRepository;

@Service
@Transactional
public class PoiCommentService {
    public record Comment(UUID id, UUID authorId, String authorName, String body, Instant createdAt, Instant updatedAt) {}
    public record Page(List<Comment> items, String nextCursor) {}
    public record Cursor(Instant time, UUID id) {}
    public record Report(UUID id, UUID commentId, String reason, String body, Instant createdAt) {}
    public record ReportPage(List<Report> items, String nextCursor) {}

    private final PoiCommentRepository comments;
    private final PoiRepository pois;
    private final Set<UUID> moderators;

    public PoiCommentService(PoiCommentRepository comments, PoiRepository pois,
            @Value("${POI_COMMENTS_MODERATOR_IDS:}") String moderators) {
        this.comments = comments;
        this.pois = pois;
        this.moderators = Arrays.stream(moderators.split(",")).map(String::strip).filter(value -> !value.isEmpty())
                .map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    public Page list(String reference, String after) {
        Cursor cursor = cursor(after);
        var items = comments.list(subject(reference), cursor);
        String next = items.size() > 20 ? encode(items.get(19).createdAt(), items.get(19).id()) : null;
        return new Page(items.stream().limit(20).toList(), next);
    }

    public Comment create(String reference, UUID author, String body) {
        String clean = text(body, 2000, "Comment");
        UUID subject = subject(reference);
        write(author);
        return comments.create(subject, author, clean);
    }

    public Comment edit(UUID id, UUID author, String body) {
        String clean = text(body, 2000, "Comment");
        Comment comment = required(id);
        if (!comment.authorId().equals(author)) throw failure(HttpStatus.FORBIDDEN, "Only the author can edit this comment.");
        write(author);
        return comments.edit(id, clean);
    }

    public void delete(UUID id, UUID author) {
        Comment comment = required(id);
        if (!comment.authorId().equals(author) && !moderators.contains(author)) {
            throw failure(HttpStatus.FORBIDDEN, "Only the author or a moderator can delete this comment.");
        }
        comments.delete(id);
    }

    public void report(UUID id, UUID reporter, String reason) {
        String clean = text(reason, 500, "Report reason");
        required(id);
        write(reporter);
        comments.report(id, reporter, clean);
    }

    public ReportPage reports(UUID moderator, String after) {
        if (!moderators.contains(moderator)) throw failure(HttpStatus.FORBIDDEN, "Moderator access is required.");
        var items = comments.reports(cursor(after));
        String next = items.size() > 20 ? encode(items.get(19).createdAt(), items.get(19).id()) : null;
        return new ReportPage(items.stream().limit(20).toList(), next);
    }

    private UUID subject(String reference) {
        validateReference(reference);
        List<String> references;
        try {
            references = pois.findCommentReferences(reference);
        } catch (DataAccessException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "Place verification is unavailable. Please try again later.");
        }
        if (references.isEmpty()) throw failure(HttpStatus.NOT_FOUND, "This place is not available for comments.");
        return comments.subject(references);
    }

    private Comment required(UUID id) {
        Comment comment = comments.find(id);
        if (comment == null) throw failure(HttpStatus.NOT_FOUND, "Comment not found.");
        return comment;
    }

    private void write(UUID author) {
        if (!comments.userExists(author)) throw failure(HttpStatus.UNAUTHORIZED, "Please sign in again.");
        if (!comments.allowWrite(author)) throw failure(HttpStatus.TOO_MANY_REQUESTS, "Too many changes. Please wait a minute and try again.");
    }

    static void validateReference(String reference) {
        if (reference == null || !reference.matches("(?:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|osm:[nwr]/[1-9][0-9]{0,18})")) {
            throw failure(HttpStatus.BAD_REQUEST, "A stable map POI identifier is required.");
        }
        if (reference.startsWith("osm:")) {
            try { Long.parseLong(reference.substring(6)); }
            catch (NumberFormatException exception) { throw failure(HttpStatus.BAD_REQUEST, "Invalid OSM identifier."); }
        }
    }

    private static String text(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.strip().length() > limit) {
            throw failure(HttpStatus.BAD_REQUEST, field + " must contain 1 to " + limit + " characters.");
        }
        return value.strip();
    }

    static Cursor cursor(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            if (value.length() > 160) throw new IllegalArgumentException();
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw failure(HttpStatus.BAD_REQUEST, "Invalid comments cursor.");
        }
    }

    static String encode(Instant time, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseStatusException failure(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}