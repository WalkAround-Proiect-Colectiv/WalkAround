package services.applicationapi.events.model;

/** The city, town or commune a user is in, as resolved from coordinates. */
public record Locality(String key, String name, String county, String countryCode) {
}
