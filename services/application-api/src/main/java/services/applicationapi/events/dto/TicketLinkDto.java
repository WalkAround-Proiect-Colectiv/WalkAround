package services.applicationapi.events.dto;

import services.applicationapi.events.model.TicketLink;

/** A public link for buying tickets or viewing more event information. */
public record TicketLinkDto(
        String source,
        String url,
        TicketLinkKind kind
) {

    public static TicketLinkDto of(TicketLink link) {
        return new TicketLinkDto(
                link.source(),
                link.url(),
                TicketLinkKind.classify(link.linkType(), link.source(), link.url())
        );
    }
}
