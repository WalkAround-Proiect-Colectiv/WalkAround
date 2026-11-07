package services.applicationapi.events.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.dto.EnrichmentStatus;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.EventCoordinates;
import services.applicationapi.events.model.EventDetails;
import services.applicationapi.events.model.EventInterest;
import services.applicationapi.events.model.EventVenue;
import services.applicationapi.events.model.TicketLink;
import services.applicationapi.events.service.EventDetailsEnrichmentService.EnrichmentResult;
import services.applicationapi.events.service.exceptions.EventNotFoundException;
import services.applicationapi.events.service.locality.EventCoordinatesResolver;
import services.applicationapi.events.service.source.RawEventDetails;

@Service
public class EventDetailsServiceImpl implements EventDetailsService {

    private final EventsService eventsService;
    private final EventInterestService interestService;
    private final EventDetailsEnrichmentService enrichmentService;
    private final EventCoordinatesResolver coordinatesResolver;
    private final EventsProperties properties;
    private final Clock clock;

    public EventDetailsServiceImpl(
            EventsService eventsService,
            EventInterestService interestService,
            EventDetailsEnrichmentService enrichmentService,
            EventCoordinatesResolver coordinatesResolver,
            EventsProperties properties,
            Clock eventsClock
    ) {
        this.eventsService = eventsService;
        this.interestService = interestService;
        this.enrichmentService = enrichmentService;
        this.coordinatesResolver = coordinatesResolver;
        this.properties = properties;
        this.clock = eventsClock;
    }

    @Override
    public EventDetails getDetails(UUID userId, String localityKey, String eventKey) {
        Optional<CachedEvent> cachedOpt = eventsService.findEvent(localityKey, eventKey);
        Optional<EventInterest> interestOpt = interestService.find(userId, eventKey);

        if (cachedOpt.isEmpty() && interestOpt.isEmpty()) {
            throw new EventNotFoundException();
        }

        CachedEvent cached = cachedOpt.orElse(null);
        EventInterest interest = interestOpt.orElse(null);

        EnrichmentResult enrichmentResult = cached != null
                ? enrichmentService.enrich(localityKey, cached)
                : enrichmentService.findCached(eventKey)
                        .map(EnrichmentResult::complete)
                        .orElseGet(EnrichmentResult::basic);

        RawEventDetails extra = enrichmentResult.details().orElse(null);
        Instant now = clock.instant();

        String resolvedLocalityKey = cached != null
                ? localityKey
                : (interest != null && hasText(interest.getLocalityKey()) ? interest.getLocalityKey() : localityKey);

        String title = firstNotBlank(
                extra != null ? extra.title() : null,
                cached != null ? cached.title() : null,
                interest != null ? interest.getTitle() : null
        );

        Instant startsAt = cached != null ? cached.startsAt() : (interest != null ? interest.getStartsAt() : now);
        boolean allDay = cached != null ? cached.allDay() : (interest != null && interest.isAllDay());
        String whenText = firstNotBlank(
                cached != null ? cached.whenText() : null,
                interest != null ? interest.getWhenText() : null,
                null
        );

        if (extra != null && hasText(extra.when())) {
            Optional<EventDateParser.ParsedDate> parsed = EventDateParser.parse(
                    null,
                    extra.when(),
                    LocalDate.ofInstant(now, properties.timeZone()),
                    properties.timeZone()
            );
            if (parsed.isPresent()) {
                startsAt = parsed.get().startsAt();
                allDay = parsed.get().allDay();
                whenText = extra.when();
            }
        }

        String extraAddress = extra != null && extra.address() != null && !extra.address().isEmpty()
                ? String.join(", ", extra.address())
                : null;
        String address = firstNotBlank(
                extraAddress,
                cached != null ? cached.address() : null,
                interest != null ? interest.getAddress() : null
        );

        String description = firstNotBlank(
                extra != null ? extra.description() : null,
                cached != null ? cached.description() : null,
                interest != null ? interest.getDescription() : null
        );

        EventVenue interestVenue = interest != null && hasText(interest.getVenueName())
                ? new EventVenue(interest.getVenueName(), null, null, null)
                : null;
        EventVenue cachedVenue = cached != null
                ? EventListBuilder.mergeVenue(
                        cached.venue(),
                        hasText(cached.venueName()) ? new EventVenue(cached.venueName(), null, null, null) : null
                )
                : null;
        EventVenue venue = EventListBuilder.mergeVenue(
                extra != null ? extra.venue() : null,
                EventListBuilder.mergeVenue(cachedVenue, interestVenue)
        );

        Double latitude = cached != null ? cached.latitude() : null;
        Double longitude = cached != null ? cached.longitude() : null;
        if (latitude == null || longitude == null) {
            Optional<EventCoordinates> resolvedCoords = coordinatesResolver.resolve(
                    localityNameFromKey(resolvedLocalityKey),
                    venue != null ? venue.name() : null,
                    address,
                    null,
                    null
            );
            if (resolvedCoords.isPresent()) {
                latitude = resolvedCoords.get().latitude();
                longitude = resolvedCoords.get().longitude();
            }
        }

        String imageUrl = firstNotBlank(
                httpsOrNull(extra != null ? extra.imageUrl() : null),
                httpsOrNull(cached != null ? cached.thumbnailUrl() : null),
                httpsOrNull(interest != null ? interest.getThumbnailUrl() : null)
        );

        String eventUrl = firstNotBlank(
                httpsOrNull(extra != null ? extra.eventUrl() : null),
                httpsOrNull(cached != null ? cached.eventUrl() : null),
                null
        );

        String mapUrl = firstNotBlank(
                httpsOrNull(extra != null ? extra.mapUrl() : null),
                httpsOrNull(cached != null ? cached.mapUrl() : null),
                null
        );

        List<TicketLink> mergedTickets = EventListBuilder.mergeTicketLinks(
                extra != null ? extra.tickets() : List.of(),
                cached != null ? cached.tickets() : List.of()
        );
        if (interest != null && httpsOrNull(interest.getTicketUrl()) != null) {
            mergedTickets = EventListBuilder.mergeTicketLinks(
                    mergedTickets,
                    List.of(new TicketLink(interest.getTicketSource(), interest.getTicketUrl(), "tickets"))
            );
        }

        boolean past = EventListBuilder.isPast(startsAt, allDay, now, properties.timeZone());
        EnrichmentStatus status = enrichmentResult.status();

        return new EventDetails(
                eventKey,
                resolvedLocalityKey,
                title != null ? title : "",
                startsAt,
                allDay,
                whenText,
                past,
                description,
                address,
                latitude,
                longitude,
                venue,
                imageUrl,
                eventUrl,
                mapUrl,
                mergedTickets,
                interest != null ? interest.getRemindBeforeMinutes() : null,
                interest != null ? interest.getRemindAt() : null,
                interest != null,
                status
        );
    }

    private static String localityNameFromKey(String localityKey) {
        if (localityKey == null || localityKey.isBlank()) {
            return null;
        }
        int lastSlash = localityKey.lastIndexOf('/');
        String tail = lastSlash >= 0 ? localityKey.substring(lastSlash + 1) : localityKey;
        return tail.replace('-', ' ').strip();
    }

    private static String firstNotBlank(String first, String second, String third) {
        if (hasText(first)) {
            return first;
        }
        if (hasText(second)) {
            return second;
        }
        return hasText(third) ? third : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String httpsOrNull(String url) {
        return url != null && url.startsWith("https://") ? url : null;
    }
}
