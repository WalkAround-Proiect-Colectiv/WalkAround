package services.applicationapi.events.service.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import services.applicationapi.events.model.EventVenue;
import services.applicationapi.events.model.TicketLink;

/** Extra details for a single event: full date (usually with the year), full address, description, venue and links. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawEventDetails(
        String title,
        String when,
        List<String> address,
        String description,
        List<TicketLink> tickets,
        String eventUrl,
        String mapUrl,
        EventVenue venue,
        String imageUrl
) {

    public RawEventDetails(
            String when,
            List<String> address,
            String description,
            List<TicketLink> tickets
    ) {
        this(null, when, address, description, tickets, null, null, null, null);
    }
}
