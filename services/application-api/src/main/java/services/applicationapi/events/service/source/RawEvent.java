package services.applicationapi.events.service.source;

import java.util.List;
import services.applicationapi.events.model.EventVenue;
import services.applicationapi.events.model.TicketLink;

/** An event exactly as the provider describes it, before date parsing and de-duplication. */
public record RawEvent(
        String title,
        String startDate,
        String when,
        String venueName,
        List<String> address,
        String thumbnailUrl,
        List<TicketLink> tickets,
        String detailsToken,
        String description,
        String eventUrl,
        String mapUrl,
        EventVenue venue
) {

    public RawEvent(
            String title,
            String startDate,
            String when,
            String venueName,
            List<String> address,
            String thumbnailUrl,
            List<TicketLink> tickets,
            String detailsToken
    ) {
        this(
                title,
                startDate,
                when,
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
