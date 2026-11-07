package services.applicationapi.events.service.source;

import java.util.List;
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
        String detailsToken
) {
}
