package services.applicationapi.tripplan.dto;

public record AirportResponse(
        Long id,
        String name,
        String iataCode,
        String countryCode,
        double latitude,
        double longitude,
        AirportMatchType matchType,
        Double distanceKm
) {
}
