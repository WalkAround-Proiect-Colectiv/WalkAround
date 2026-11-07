package services.applicationapi.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.EventLocalitySnapshot;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.repository.EventLocalitySnapshotRepository;
import services.applicationapi.events.service.EventsService;
import services.applicationapi.events.service.EventsService.NearbyEvents;
import services.applicationapi.events.service.exceptions.EventsUnavailableException;
import services.applicationapi.events.service.source.EventSource;
import services.applicationapi.events.service.source.RawEvent;
import services.applicationapi.events.service.source.RawEventDetails;
import tools.jackson.databind.json.JsonMapper;

class EventsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final Locality CLUJ = new Locality("RO/cluj/cluj-napoca", "Cluj-Napoca", "Cluj", "RO");

    private final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 11, 5, 18, 0).atZone(ZONE).toInstant());
    private final CountingSource source = new CountingSource();
    private final InMemorySnapshots snapshots = new InMemorySnapshots();
    private Optional<Locality> locality;

    private EventsService service;

    @BeforeEach
    void setUp() {
        locality = Optional.of(CLUJ);
        service = newService();
    }

    private EventsService newService() {
        EventsProperties properties = new EventsProperties("recorded", null, URI.create("https://serpapi.com"),
                Duration.ofSeconds(30), Duration.ofHours(24), ZONE, URI.create("https://nominatim.openstreetmap.org"),
                Duration.ofSeconds(5), "test-agent", false, "event-reminders-topic");
        return new EventsService((lat, lng) -> locality, source, snapshots, properties,
                JsonMapper.builder().build(), clock);
    }

    @Test
    void fetchesOnceAndThenServesFromMemory() {
        NearbyEvents first = service.nearby(46.77, 23.59);
        NearbyEvents second = service.nearby(46.77, 23.59);

        assertThat(first.events()).extracting("title").containsExactly("Opera night");
        assertThat(second.events()).hasSize(1);
        assertThat(source.searches.get()).isEqualTo(1);
        assertThat(first.stale()).isFalse();
    }

    @Test
    void reusesTheDatabaseSnapshotAfterARestart() {
        service.nearby(46.77, 23.59);

        NearbyEvents afterRestart = newService().nearby(46.77, 23.59);

        assertThat(afterRestart.events()).hasSize(1);
        assertThat(source.searches.get()).isEqualTo(1);
    }

    @Test
    void refetchesWhenTheCacheIsOlderThanTheTtl() {
        service.nearby(46.77, 23.59);
        clock.advance(Duration.ofHours(25));

        service.nearby(46.77, 23.59);

        assertThat(source.searches.get()).isEqualTo(2);
    }

    @Test
    void servesTheStaleSnapshotWhenTheSourceFails() {
        service.nearby(46.77, 23.59);
        clock.advance(Duration.ofHours(25));
        source.failing = true;

        NearbyEvents result = newService().nearby(46.77, 23.59);

        assertThat(result.stale()).isTrue();
        assertThat(result.events()).hasSize(1);
    }

    @Test
    void failsWhenTheSourceFailsAndNothingIsCached() {
        source.failing = true;

        assertThatThrownBy(() -> service.nearby(46.77, 23.59)).isInstanceOf(EventsUnavailableException.class);
    }

    @Test
    void returnsNoEventsWithoutCallingTheSourceOutsideAnyLocality() {
        locality = Optional.empty();

        NearbyEvents result = service.nearby(45.0, 30.0);

        assertThat(result.locality()).isEmpty();
        assertThat(result.events()).isEmpty();
        assertThat(source.searches.get()).isZero();
    }

    @Test
    void hidesEventsThatEndedSinceTheyWereCached() {
        source.startDate = "Nov 5";
        source.when = "Nov 5, 7:00 PM";
        assertThat(service.nearby(46.77, 23.59).events()).hasSize(1);
        clock.advance(Duration.ofHours(4));

        assertThat(service.nearby(46.77, 23.59).events()).isEmpty();
    }

    @Test
    void findsCachedEventsByKey() {
        String key = service.nearby(46.77, 23.59).events().getFirst().key();

        assertThat(service.findEvent(CLUJ.key(), key)).isPresent();
        assertThat(service.findEvent(CLUJ.key(), "missing")).isEmpty();
    }

    private static final class CountingSource implements EventSource {
        final AtomicInteger searches = new AtomicInteger();
        boolean failing;
        String startDate = "Nov 6";
        String when = "Nov 6, 7:00 PM";

        @Override
        public List<RawEvent> search(Locality locality) {
            searches.incrementAndGet();
            if (failing) {
                throw new EventsUnavailableException();
            }
            return List.of(new RawEvent("Opera night", startDate, when, "Opera",
                    List.of("Opera", "Cluj-Napoca"), null, List.of(), "token"));
        }

        @Override
        public Optional<RawEventDetails> details(String detailsToken) {
            return Optional.empty();
        }
    }

    private static final class InMemorySnapshots implements EventLocalitySnapshotRepository {
        private final Map<String, EventLocalitySnapshot> rows = new HashMap<>();

        @Override
        public EventLocalitySnapshot save(EventLocalitySnapshot snapshot) {
            rows.put(snapshot.getLocalityKey(), snapshot);
            return snapshot;
        }

        @Override
        public Optional<EventLocalitySnapshot> findById(String localityKey) {
            return Optional.ofNullable(rows.get(localityKey));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
