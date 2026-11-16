package services.applicationapi.tripplan.dto;

public record TrainLegResponse(
        String mode,
        String company,
        String lineNumber,
        String departureTime,
        String arrivalTime,
        String departureZone,
        String arrivalZone,
        Integer durationMinutes
) {
}
