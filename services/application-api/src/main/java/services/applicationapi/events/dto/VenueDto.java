package services.applicationapi.events.dto;

/** Venue metadata shown on the event details screen. */
public record VenueDto(
        String name,
        Double rating,
        Integer reviewCount,
        String url
) {
}
