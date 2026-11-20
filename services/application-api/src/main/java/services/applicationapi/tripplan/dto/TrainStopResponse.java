package services.applicationapi.tripplan.dto;

public record TrainStopResponse(
        Long positionId,
        String name,
        String city,
        String countryCode
) {
}
