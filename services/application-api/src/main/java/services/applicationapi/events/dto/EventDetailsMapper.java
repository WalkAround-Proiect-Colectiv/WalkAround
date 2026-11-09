package services.applicationapi.events.dto;

import java.util.List;
import org.springframework.stereotype.Component;
import services.applicationapi.events.model.EventDetails;
import services.applicationapi.events.model.EventVenue;

/** Pure domain-to-DTO mapper for the event details endpoint. */
@Component
public class EventDetailsMapper {

    public EventDetailsResponse toResponse(EventDetails details, String attribution) {
        List<TicketLinkDto> ticketDtos = details.ticketLinks() == null
                ? List.of()
                : details.ticketLinks().stream().map(TicketLinkDto::of).toList();

        EventDetailsDto eventDto = new EventDetailsDto(
                details.key(),
                details.localityKey(),
                details.title(),
                details.startsAt(),
                details.allDay(),
                details.whenText(),
                details.past(),
                details.description(),
                details.address(),
                details.latitude(),
                details.longitude(),
                toVenueDto(details.venue()),
                details.imageUrl(),
                details.eventUrl(),
                details.mapUrl(),
                ticketDtos
        );

        InterestStateDto interestDto = details.interested()
                ? new InterestStateDto(details.remindBeforeMinutes(), details.remindAt())
                : null;

        return new EventDetailsResponse(
                eventDto,
                interestDto,
                details.enrichment(),
                attribution
        );
    }

    public VenueDto toVenueDto(EventVenue venue) {
        if (venue == null) {
            return null;
        }
        if ((venue.name() == null || venue.name().isBlank())
                && venue.rating() == null
                && venue.reviewCount() == null
                && venue.url() == null) {
            return null;
        }
        return new VenueDto(
                venue.name(),
                venue.rating(),
                venue.reviewCount(),
                venue.url()
        );
    }
}
