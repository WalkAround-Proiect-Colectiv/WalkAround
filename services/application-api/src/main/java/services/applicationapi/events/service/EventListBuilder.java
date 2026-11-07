package services.applicationapi.events.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import services.applicationapi.events.dto.TicketLinkKind;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.EventVenue;
import services.applicationapi.events.model.TicketLink;
import services.applicationapi.events.service.locality.Slugs;
import services.applicationapi.events.service.source.RawEvent;

/**
 * Cleans provider results: parses dates, drops past events and duplicates (the two queries per locality
 * overlap), and sorts by start time.
 */
public final class EventListBuilder {

    /** Timed events stay listed for a while after they start, since they are usually still going on. */
    public static final Duration STARTED_GRACE = Duration.ofHours(2);

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
            unique.merge(event.key(), event, EventListBuilder::mergeDuplicate);
        }
        return upcoming(unique.values().stream().toList(), now, zone);
    }

    /** Events that have not finished yet, by start time. */
    public static List<CachedEvent> upcoming(List<CachedEvent> events, Instant now, ZoneId zone) {
        LocalDate today = LocalDate.ofInstant(now, zone);
        return events.stream()
                .filter(event -> !isPast(event.startsAt(), event.allDay(), now, zone))
                .sorted(Comparator.comparing(CachedEvent::startsAt).thenComparing(CachedEvent::title))
                .toList();
    }

    /** Whether an event is considered to have already taken place. */
    public static boolean isPast(Instant startsAt, boolean allDay, Instant now, ZoneId zone) {
        if (startsAt == null) {
            return false;
        }
        LocalDate today = LocalDate.ofInstant(now, zone);
        return allDay
                ? LocalDate.ofInstant(startsAt, zone).isBefore(today)
                : !startsAt.plus(STARTED_GRACE).isAfter(now);
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
        String venueName = raw.venueName() != null && !raw.venueName().isBlank()
                ? raw.venueName()
                : (raw.venue() != null ? raw.venue().name() : null);
        EventVenue venue = sanitizeVenue(raw.venue(), venueName);
        return new CachedEvent(
                eventKey(raw.title(), date.startsAt(), venueName, zone),
                raw.title(),
                date.startsAt(),
                date.allDay(),
                raw.when(),
                venueName,
                address == null || address.isBlank() ? null : address,
                httpsOrNull(raw.thumbnailUrl()),
                raw.tickets() == null ? List.of() : raw.tickets(),
                raw.detailsToken(),
                blankToNull(raw.description()),
                httpsOrNull(raw.eventUrl()),
                httpsOrNull(raw.mapUrl()),
                venue
        );
    }

    private static CachedEvent mergeDuplicate(CachedEvent first, CachedEvent second) {
        return new CachedEvent(
                first.key(),
                first.title(),
                first.startsAt(),
                first.allDay(),
                firstNotBlank(first.whenText(), second.whenText()),
                firstNotBlank(first.venueName(), second.venueName()),
                firstNotBlank(first.address(), second.address()),
                firstNotBlank(first.thumbnailUrl(), second.thumbnailUrl()),
                mergeTicketLinks(first.tickets(), second.tickets()),
                firstNotBlank(first.detailsToken(), second.detailsToken()),
                firstNotBlank(first.description(), second.description()),
                firstNotBlank(first.eventUrl(), second.eventUrl()),
                firstNotBlank(first.mapUrl(), second.mapUrl()),
                mergeVenue(first.venue(), second.venue()),
                first.latitude() != null ? first.latitude() : second.latitude(),
                first.longitude() != null ? first.longitude() : second.longitude()
        );
    }

    public static List<TicketLink> mergeTicketLinks(List<TicketLink> primary, List<TicketLink> secondary) {
        Map<String, TicketLink> byUrl = new LinkedHashMap<>();
        for (List<TicketLink> list : List.of(
                primary == null ? List.<TicketLink>of() : primary,
                secondary == null ? List.<TicketLink>of() : secondary
        )) {
            for (TicketLink link : list) {
                if (link == null || httpsOrNull(link.url()) == null) {
                    continue;
                }
                byUrl.merge(link.url(), link, (existing, candidate) -> {
                    TicketLinkKind existingKind = TicketLinkKind.fromLinkType(existing.linkType());
                    TicketLinkKind candidateKind = TicketLinkKind.fromLinkType(candidate.linkType());
                    String chosenType = existingKind == TicketLinkKind.TICKETS
                            ? existing.linkType()
                            : (candidateKind == TicketLinkKind.TICKETS ? candidate.linkType() : existing.linkType());
                    String chosenSource = firstNotBlank(existing.source(), candidate.source());
                    return new TicketLink(chosenSource, existing.url(), chosenType);
                });
            }
        }
        List<TicketLink> merged = new ArrayList<>(byUrl.values());
        merged.sort(Comparator.comparingInt(
                link -> TicketLinkKind.fromLinkType(link.linkType()) == TicketLinkKind.TICKETS ? 0 : 1
        ));
        return List.copyOf(merged);
    }

    public static EventVenue mergeVenue(EventVenue primary, EventVenue secondary) {
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        return new EventVenue(
                firstNotBlank(primary.name(), secondary.name()),
                primary.rating() != null ? primary.rating() : secondary.rating(),
                primary.reviewCount() != null ? primary.reviewCount() : secondary.reviewCount(),
                firstNotBlank(primary.url(), secondary.url())
        );
    }

    private static EventVenue sanitizeVenue(EventVenue venue, String fallbackName) {
        if (venue == null) {
            return fallbackName == null || fallbackName.isBlank()
                    ? null
                    : new EventVenue(fallbackName, null, null, null);
        }
        Double rating = venue.rating() != null && venue.rating() >= 0.0 && venue.rating() <= 5.0
                ? venue.rating()
                : null;
        Integer reviews = venue.reviewCount() != null && venue.reviewCount() >= 0
                ? venue.reviewCount()
                : null;
        String name = firstNotBlank(venue.name(), fallbackName);
        String url = httpsOrNull(venue.url());
        if (name == null && rating == null && reviews == null && url == null) {
            return null;
        }
        return new EventVenue(name, rating, reviews, url);
    }

    private static String firstNotBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : blankToNull(second);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String httpsOrNull(String url) {
        return url != null && url.startsWith("https://") ? url : null;
    }
}
