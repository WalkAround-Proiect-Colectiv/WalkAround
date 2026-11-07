package services.applicationapi.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.TicketLink;
import services.applicationapi.events.service.EventListBuilder;
import services.applicationapi.events.service.source.RawEvent;

class EventListBuilderTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final Instant NOW = LocalDateTime.of(2026, 11, 5, 18, 0).atZone(ZONE).toInstant();

    private static RawEvent raw(String title, String startDate, String when, String venue) {
        return new RawEvent(title, startDate, when, venue, List.of(venue, "Cluj-Napoca, Romania"),
                "https://example.com/thumb.jpg", List.of(new TicketLink("bilete.ro", "https://www.bilete.ro/x")),
                "token-" + title);
    }

    @Test
    void removesDuplicatesFromOverlappingQueries() {
        List<CachedEvent> events = EventListBuilder.build(List.of(
                raw("THE RITE OF SPRING", "Nov 6", "Nov 6, 8:00 PM", "National Theatre"),
                raw("The Rite of Spring", "Nov 6", "Nov 6, 8:00 PM", "National Theatre"),
                raw("Salsa Fest", "Nov 19", "Nov 19, 7:00 PM", "Grand Hotel")), NOW, ZONE);

        assertThat(events).extracting(CachedEvent::title).containsExactly("THE RITE OF SPRING", "Salsa Fest");
    }

    @Test
    void dropsPastEventsAndSortsByStartTime() {
        List<CachedEvent> events = EventListBuilder.build(List.of(
                raw("Later", "Nov 19", "Nov 19, 7:00 PM", "A"),
                raw("Yesterday", "Nov 4", "Nov 4, 2026, 8:00 PM", "B"),
                raw("Started an hour ago", "Nov 5", "Nov 5, 5:00 PM", "C"),
                raw("Ended", "Nov 5", "Nov 5, 2:00 PM", "D"),
                raw("Sooner", "Nov 6", "Nov 6, 8:00 PM", "E"),
                raw("No date", null, "Soon", "F")), NOW, ZONE);

        assertThat(events).extracting(CachedEvent::title)
                .containsExactly("Started an hour ago", "Sooner", "Later");
    }

    @Test
    void eventKeysAreStableAndIgnoreCaseAndAccents() {
        Instant startsAt = LocalDateTime.of(2026, 11, 6, 20, 0).atZone(ZONE).toInstant();

        String key = EventListBuilder.eventKey("Concert la Ateneu", startsAt, "Sala Patria", ZONE);

        assertThat(key).hasSize(32)
                .isEqualTo(EventListBuilder.eventKey("CONCERT LA ATENEU", startsAt.plusSeconds(3600), "Sală Patria", ZONE));
        assertThat(key).isNotEqualTo(EventListBuilder.eventKey("Concert la Ateneu", startsAt, "Other venue", ZONE));
    }

    @Test
    void keepsOnlyHttpsThumbnails() {
        RawEvent insecure = new RawEvent("Show", "Nov 6", "Nov 6, 8:00 PM", "Venue", List.of(),
                "http://example.com/x.jpg", List.of(), null);

        assertThat(EventListBuilder.build(List.of(insecure), NOW, ZONE).getFirst().thumbnailUrl()).isNull();
    }
}
