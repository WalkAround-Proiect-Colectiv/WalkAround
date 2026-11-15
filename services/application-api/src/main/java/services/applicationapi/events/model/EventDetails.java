package services.applicationapi.events.model;

import java.time.Instant;
import java.util.List;
import services.applicationapi.events.dto.EnrichmentStatus;

/** Merged domain representation of an event for the details screen. */
public record EventDetails(
        String key,
        String localityKey,
        String title,
        Instant startsAt,
        boolean allDay,
        String whenText,
        boolean past,
        String description,
        String address,
        Double latitude,
        Double longitude,
        EventVenue venue,
        String imageUrl,
        String eventUrl,
        String mapUrl,
        List<TicketLink> ticketLinks,
        Integer remindBeforeMinutes,
        Instant remindAt,
        boolean interested,
        EnrichmentStatus enrichment
) {
}
