package services.applicationapi.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.service.EventListBuilder;
import services.applicationapi.events.service.source.RawEvent;
import services.applicationapi.events.service.source.RecordedEventSource;
import tools.jackson.databind.json.JsonMapper;

class RecordedEventSourceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final Instant RECORDED_DAY = LocalDateTime.of(2026, 11, 5, 9, 0).atZone(ZONE).toInstant();

    private final RecordedEventSource source = new RecordedEventSource(JsonMapper.builder().build());

    @Test
    void replaysTheRecordedClujResponsesWithoutDuplicates() {
        List<RawEvent> raw = source.search(new Locality("RO/cluj/cluj-napoca", "Cluj-Napoca", "Cluj", "RO"));

        List<CachedEvent> events = EventListBuilder.build(raw, RECORDED_DAY, ZONE);

        assertThat(raw).hasSize(19);
        assertThat(events).hasSize(14);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.title()).isNotBlank();
            assertThat(event.startsAt()).isAfter(RECORDED_DAY.minusSeconds(86400));
        });
        assertThat(events).extracting(CachedEvent::title).contains("THE RITE OF SPRING");
    }

    @Test
    void matchesLocalityNamesWithAccents() {
        assertThat(source.search(new Locality("RO/cluj/floresti", "Florești", "Cluj", "RO"))).isNotEmpty();
        assertThat(source.search(new Locality("RO/brasov/brasov", "Brașov", "Brașov", "RO"))).isNotEmpty();
    }

    @Test
    void returnsNothingForLocalitiesWithoutARecording() {
        assertThat(source.search(new Locality("RO/alba/rosia-montana", "Roșia Montană", "Alba", "RO"))).isEmpty();
    }
}
