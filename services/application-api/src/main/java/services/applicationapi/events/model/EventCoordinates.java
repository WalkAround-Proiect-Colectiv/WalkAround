package services.applicationapi.events.model;

/** Geographic coordinates (WGS84) resolved for an event's venue or address. */
public record EventCoordinates(double latitude, double longitude) {
}
