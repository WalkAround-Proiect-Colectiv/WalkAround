package services.applicationapi.events.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Venue metadata attached to an event. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventVenue(
        String name,
        Double rating,
        Integer reviewCount,
        String url
) {
}
