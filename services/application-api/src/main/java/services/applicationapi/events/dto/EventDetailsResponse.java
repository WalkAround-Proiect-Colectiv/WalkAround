package services.applicationapi.events.dto;

/**
 * Response payload for {@code GET /api/events/details}.
 *
 * <p>{@code event} contains the merged event information; {@code interest} is present when the signed-in
 * user marked the event as interested; {@code enrichment} reports whether extra provider details were merged.
 */
public record EventDetailsResponse(
        EventDetailsDto event,
        InterestStateDto interest,
        EnrichmentStatus enrichment,
        String attribution
) {
}
