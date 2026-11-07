package services.applicationapi.events.dto;

import java.util.Locale;

/**
 * Whether an external event link sells tickets or provides general event information.
 * Unknown or missing provider link types default to {@link #INFO} so the UI never claims a link
 * sells tickets when that is not certain.
 */
public enum TicketLinkKind {
    TICKETS,
    INFO;

    public static TicketLinkKind fromLinkType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return INFO;
        }
        String normalized = linkType.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tickets", "ticket", "buy", "registration" -> TICKETS;
            default -> INFO;
        };
    }
}
