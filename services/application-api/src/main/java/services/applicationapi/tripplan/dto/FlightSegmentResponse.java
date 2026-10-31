package services.applicationapi.tripplan.dto;

public record FlightSegmentResponse(
        String departureAirport,
        String departureTime,
        String arrivalAirport,
        String arrivalTime,
        String flightNumber,
        String airline
) {
}
