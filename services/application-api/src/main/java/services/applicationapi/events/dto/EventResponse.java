package services.applicationapi.events.dto;

import java.time.Instant;
import java.util.List;
import services.applicationapi.events.model.CachedEvent;

/** An upcoming event as shown in the list. {@code interested} tells whether the current user marked it. */
public record EventResponse(
        String key,
        String localityKey,
        String title,
        Instant startsAt,
        boolean allDay,
        String whenText,
        String venueName,
        String address,
        String thumbnailUrl,
        List<TicketLinkDto> tickets,
        boolean interested,
        Integer remindBeforeMinutes
) {

    public static EventResponse of(CachedEvent event, String localityKey, InterestResponse interest) {
        List<TicketLinkDto> ticketDtos = event.tickets() == null
                ? List.of()
                : event.tickets().stream().map(TicketLinkDto::of).toList();
        return new EventResponse(
                event.key(), localityKey, event.title(), event.startsAt(), event.allDay(), event.whenText(),
                event.venueName(), event.address(), event.thumbnailUrl(), ticketDtos,
                interest != null, interest == null ? null : interest.remindBeforeMinutes());
    }
}
