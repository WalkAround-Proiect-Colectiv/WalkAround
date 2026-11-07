package services.applicationapi.events.model;

import java.time.Instant;
import java.util.List;

/**
 * One upcoming event as kept in the locality cache. {@code detailsToken} is the provider's
 * time-limited token for fetching full details; it never leaves the server.
 */
public record CachedEvent(
        String key,
        String title,
        Instant startsAt,
        boolean allDay,
        String whenText,
        String venueName,
        String address,
        String thumbnailUrl,
        List<TicketLink> tickets,
        String detailsToken
) {
}
