package services.applicationapi.tripplan.dto;

public record BusStopResponse(
        Long positionId,
        String name,
        String city,
        String countryCode
) {
}
