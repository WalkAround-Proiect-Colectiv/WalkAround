package services.applicationapi.poicomments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.poicomments.repository.PoiCommentRepository;
import services.applicationapi.poicomments.service.PoiCommentService;
import services.applicationapi.poicomments.service.PoiCommentService.Comment;
import services.applicationapi.recommendation.repository.PoiRepository;

class PoiCommentServiceTest {
    private final PoiCommentRepository comments = mock(PoiCommentRepository.class);
    private final PoiRepository pois = mock(PoiRepository.class);
    private final UUID author = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID subject = UUID.randomUUID();
    private final UUID id = UUID.randomUUID();
    private PoiCommentService service;

    @BeforeEach
    void setup() {
        service = new PoiCommentService(comments, pois, moderator.toString());
        when(pois.findCommentReferences("osm:n/123")).thenReturn(List.of("osm:n/123"));
        when(comments.subject(any())).thenReturn(subject);
        when(comments.userExists(author)).thenReturn(true);
        when(comments.allowWrite(author)).thenReturn(true);
        when(comments.find(id)).thenReturn(new Comment(id, author, "Walker", "Hello", Instant.now(), Instant.now()));
    }

    @Test
    void verifiesPlaceAndNormalizesTextBeforeWriting() {
        service.create("osm:n/123", author, "  Entrance on the left  ");
        verify(comments).create(subject, author, "Entrance on the left");
    }

    @Test
    void rejectsUnidentifiedAndUnknownPlaces() {
        for (String reference : List.of("selected-coordinate", "Cafe", "osm:n/9999999999999999999")) {
            expectStatus(() -> service.create(reference, author, "Hello"), 400);
        }
        expectStatus(() -> service.list("osm:n/456", null), 404);
        verify(comments, never()).create(any(), any(), any());
    }

    @Test
    void rejectsBlankOversizedAndRateLimitedWrites() {
        expectStatus(() -> service.create("osm:n/123", author, "  "), 400);
        expectStatus(() -> service.create("osm:n/123", author, "x".repeat(2001)), 400);
        when(comments.allowWrite(author)).thenReturn(false);
        expectStatus(() -> service.create("osm:n/123", author, "Hello"), 429);
        verify(comments, never()).create(any(), any(), any());
    }

    @Test
    void editingAndDeletionRequireOwnershipButModeratorsCanDelete() {
        expectStatus(() -> service.edit(id, stranger, "Changed"), 403);
        expectStatus(() -> service.delete(id, stranger), 403);
        expectStatus(() -> service.edit(id, moderator, "Changed"), 403);
        service.edit(id, author, "Changed");
        verify(comments).edit(id, "Changed");
        service.delete(id, moderator);
        verify(comments).delete(id);
    }

    @Test
    void returnsTwentyItemsAndAValidatedContinuationCursor() {
        List<Comment> items = IntStream.range(0, 21).mapToObj(index ->
                new Comment(UUID.randomUUID(), author, "Walker", "Comment", Instant.now(), Instant.now())).toList();
        when(comments.list(subject, null)).thenReturn(items);
        var first = service.list("osm:n/123", null);
        assertThat(first.items()).hasSize(20);
        assertThat(first.nextCursor()).isNotBlank();
        when(comments.list(eq(subject), any())).thenReturn(List.of());
        assertThat(service.list("osm:n/123", first.nextCursor()).nextCursor()).isNull();
        expectStatus(() -> service.list("osm:n/123", "bad cursor"), 400);
    }

    @Test
    void reportsRequireAReasonAndModerationIsExplicitlyRestricted() {
        expectStatus(() -> service.report(id, author, " "), 400);
        service.report(id, author, "Spam");
        verify(comments).report(id, author, "Spam");
        expectStatus(() -> service.reports(stranger, null), 403);
        when(comments.reports(null)).thenReturn(List.of());
        assertThat(service.reports(moderator, null).items()).isEmpty();
    }

    @Test
    void mapDatabaseFailureIsUnavailableNotAnEmptyDiscussion() {
        when(pois.findCommentReferences(any())).thenThrow(new DataAccessResourceFailureException("private database details"));
        expectStatus(() -> service.list("osm:n/123", null), 503);
    }

    private void expectStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value()).isEqualTo(status));
    }
}