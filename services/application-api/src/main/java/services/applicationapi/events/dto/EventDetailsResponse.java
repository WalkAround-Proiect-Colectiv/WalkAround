package services.applicationapi.events.dto;

/**
 * One event for the details screen. {@code event} comes from the locality cache and may be null when the
 * cache no longer has it; {@code interest} is present when the user marked the event.
 */
public record EventDetailsResponse(EventResponse event, InterestResponse interest) {
}
