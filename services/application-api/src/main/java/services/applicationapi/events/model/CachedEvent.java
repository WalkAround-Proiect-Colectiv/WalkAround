package services.applicationapi.events.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;

/**
 * One upcoming event as kept in the locality cache. {@code detailsToken} is the provider's
 * time-limited token for fetching full details; it never leaves the server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        String detailsToken,
        String description,
        String eventUrl,
        String mapUrl,
        EventVenue venue
) {

    public CachedEvent(
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
        this(
                key,
                title,
                startsAt,
                allDay,
                whenText,
                venueName,
                address,
                thumbnailUrl,
                tickets,
                detailsToken,
                null,
                null,
                null,
                venueName == null || venueName.isBlank() ? null : new EventVenue(venueName, null, null, null)
        );
    }
}
