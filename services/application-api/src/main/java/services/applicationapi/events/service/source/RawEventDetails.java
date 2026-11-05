package services.applicationapi.events.service.source;

import java.util.List;
import services.applicationapi.events.model.TicketLink;

/** Extra details for a single event: full date (usually with the year), full address and description. */
public record RawEventDetails(
        String when,
        List<String> address,
        String description,
        List<TicketLink> tickets
) {
}
