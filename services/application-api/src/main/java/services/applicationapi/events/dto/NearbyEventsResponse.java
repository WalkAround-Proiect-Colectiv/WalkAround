package services.applicationapi.events.dto;

import java.time.Instant;
import java.util.List;

/**
 * Events for the user's locality. {@code locality} is null when the position is not inside any town.
 * {@code stale} is true when the list could not be refreshed and older cached results are shown.
 */
public record NearbyEventsResponse(
        LocalityResponse locality,
        List<EventResponse> events,
        Instant fetchedAt,
        boolean stale,
        String attribution
) {
}
