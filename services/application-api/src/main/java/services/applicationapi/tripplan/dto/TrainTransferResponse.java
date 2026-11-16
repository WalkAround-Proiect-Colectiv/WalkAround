package services.applicationapi.tripplan.dto;

public record TrainTransferResponse(
        String type,
        Integer afterLeg,
        Integer durationMinutes,
        TrainStopResponse fromStop,
        TrainStopResponse toStop
) {
}
