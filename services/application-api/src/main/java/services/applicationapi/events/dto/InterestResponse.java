package services.applicationapi.events.dto;

import java.time.Instant;
import services.applicationapi.events.model.EventInterest;

/** A marked event with its saved details and reminder state. */
public record InterestResponse(
        String eventKey,
        String localityKey,
        String title,
        Instant startsAt,
        boolean allDay,
        String whenText,
        String venueName,
        String address,
        String description,
        String thumbnailUrl,
        String ticketSource,
        String ticketUrl,
        Integer remindBeforeMinutes,
        Instant remindAt,
        Instant remindedAt
) {

    public static InterestResponse of(EventInterest interest) {
        return new InterestResponse(
                interest.getEventKey(), interest.getLocalityKey(), interest.getTitle(), interest.getStartsAt(),
                interest.isAllDay(), interest.getWhenText(), interest.getVenueName(), interest.getAddress(),
                interest.getDescription(), interest.getThumbnailUrl(), interest.getTicketSource(),
                interest.getTicketUrl(), interest.getRemindBeforeMinutes(), interest.getRemindAt(),
                interest.getRemindedAt());
    }
}
