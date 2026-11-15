package services.applicationapi.events.dto;

import java.time.Instant;
import java.util.List;

/** Merged presentation-ready details for a single event. */
public record EventDetailsDto(
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
        VenueDto venue,
        String imageUrl,
        String eventUrl,
        String mapUrl,
        List<TicketLinkDto> ticketLinks
) {
}
