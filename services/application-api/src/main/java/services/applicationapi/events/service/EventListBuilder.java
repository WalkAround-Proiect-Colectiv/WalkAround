package services.applicationapi.events.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.service.locality.Slugs;
import services.applicationapi.events.service.source.RawEvent;

/**
 * Cleans provider results: parses dates, drops past events and duplicates (the two queries per locality
 * overlap), and sorts by start time.
 */
public final class EventListBuilder {

    /** Timed events stay listed for a while after they start, since they are usually still going on. */
    static final Duration STARTED_GRACE = Duration.ofHours(2);

    private EventListBuilder() {
    }

    public static List<CachedEvent> build(List<RawEvent> rawEvents, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        Map<String, CachedEvent> unique = new LinkedHashMap<>();
        for (RawEvent raw : rawEvents) {
            Optional<EventDateParser.ParsedDate> date = EventDateParser.parse(raw.startDate(), raw.when(), today, zone);
            if (date.isEmpty()) {
                continue;
            }
            CachedEvent event = toCachedEvent(raw, date.get(), zone);
            unique.putIfAbsent(event.key(), event);
        }
        return upcoming(unique.values().stream().toList(), now, zone);
    }

    /** Events that have not finished yet, by start time. */
    public static List<CachedEvent> upcoming(List<CachedEvent> events, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        return events.stream()
                .filter(event -> event.allDay()
                        ? !LocalDate.ofInstant(event.startsAt(), zone).isBefore(today)
                        : event.startsAt().plus(STARTED_GRACE).isAfter(now))
                .sorted(Comparator.comparing(CachedEvent::startsAt).thenComparing(CachedEvent::title))
                .toList();
    }

    /** Stable identifier for an event: the same title, day and venue always produce the same key. */
    public static String eventKey(String title, Instant startsAt, String venueName, ZoneId zone) {
        String day = LocalDate.ofInstant(startsAt, zone).toString();
        String source = Slugs.slug(title) + "|" + day + "|" + Slugs.slug(venueName);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static CachedEvent toCachedEvent(RawEvent raw, EventDateParser.ParsedDate date, ZoneId zone) {
        String address = raw.address() == null ? null : String.join(", ", raw.address());
        return new CachedEvent(
                eventKey(raw.title(), date.startsAt(), raw.venueName(), zone),
                raw.title(),
                date.startsAt(),
                date.allDay(),
                raw.when(),
                raw.venueName(),
                address == null || address.isBlank() ? null : address,
                httpsOrNull(raw.thumbnailUrl()),
                raw.tickets() == null ? List.of() : raw.tickets(),
                raw.detailsToken());
    }

    private static String httpsOrNull(String url) {
        return url != null && url.startsWith("https://") ? url : null;
    }
}
