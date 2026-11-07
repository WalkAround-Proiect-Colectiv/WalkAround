package services.applicationapi.events.model;

import java.time.Instant;

/** Event details copied into an interest so reminders work without calling the provider again. */
public record EventSnapshot(
        String title,
        Instant startsAt,
        boolean allDay,
        String whenText,
        String venueName,
        String address,
        String description,
        String thumbnailUrl,
        String ticketSource,
        String ticketUrl
) {
}
