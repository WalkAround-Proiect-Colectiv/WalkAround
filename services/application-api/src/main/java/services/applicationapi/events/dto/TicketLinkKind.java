package services.applicationapi.events.dto;

import java.util.List;
import java.util.Locale;

/**
 * Whether an external event link sells tickets or provides general event information.
 * Explicit ticket link types and recognized ticketing platforms resolve to {@link #TICKETS};
 * general or informational links resolve to {@link #INFO}.
 */
public enum TicketLinkKind {
    TICKETS,
    INFO;

    private static final List<String> TICKETING_PLATFORMS = List.of(
            "bilete.ro",
            "iabilet",
            "eventim",
            "livetickets",
            "entertix",
            "ambilet",
            "eventbook",
            "biletmaster",
            "mystage",
            "blt.ro",
            "kompostor",
            "ove.ro",
            "biletin",
            "extasy",
            "eventbrite",
            "ticketmaster",
            "ticketswap",
            "seetickets",
            "dice.fm",
            "feverup",
            "bandsintown",
            "songkick",
            "tixr",
            "shotgun.live",
            "residentadvisor"
    );

    private static final List<String> TICKETING_URL_PATTERNS = List.of(
            "/bilete",
            "/bilet/",
            "/tickets",
            "/ticket/",
            "/buy-tickets"
    );

    public static TicketLinkKind fromLinkType(String linkType) {
        return classify(linkType, null, null);
    }

    public static TicketLinkKind classify(String linkType, String source, String url) {
        if (isExplicitTicketType(linkType)) {
            return TICKETS;
        }
        if (isTicketingPlatform(source, url)) {
            return TICKETS;
        }
        return INFO;
    }

    private static boolean isExplicitTicketType(String linkType) {
        if (linkType == null || linkType.isBlank()) {
            return false;
        }
        String normalized = linkType.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tickets", "ticket", "buy", "buy tickets", "registration", "register" -> true;
            default -> false;
        };
    }

    private static boolean isTicketingPlatform(String source, String url) {
        String normalizedSource = source == null ? "" : source.strip().toLowerCase(Locale.ROOT);
        String normalizedUrl = url == null ? "" : url.strip().toLowerCase(Locale.ROOT);

        for (String platform : TICKETING_PLATFORMS) {
            if (normalizedSource.contains(platform) || normalizedUrl.contains(platform)) {
                return true;
            }
        }
        for (String pattern : TICKETING_URL_PATTERNS) {
            if (normalizedUrl.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
