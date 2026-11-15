package services.applicationapi.events.dto;

import java.time.Instant;

/** The signed-in user's reminder state for an event they marked as interested. */
public record InterestStateDto(
        Integer remindBeforeMinutes,
        Instant remindAt
) {
}
