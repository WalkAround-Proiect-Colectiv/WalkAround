package services.applicationapi.tripplan.dto;

public record AirportResponse(
        Long id,
        String name,
        String iataCode,
        double latitude,
        double longitude
) {
}
