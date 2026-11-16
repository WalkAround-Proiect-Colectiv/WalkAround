package services.applicationapi.tripplan.dto;

public record BusTransferResponse(
        String type,
        Integer afterLeg,
        Integer durationMinutes,
        BusStopResponse fromStop,
        BusStopResponse toStop
) {
}
