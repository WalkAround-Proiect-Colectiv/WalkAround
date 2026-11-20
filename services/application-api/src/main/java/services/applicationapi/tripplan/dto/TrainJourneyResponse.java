package services.applicationapi.tripplan.dto;

import java.util.List;

public record TrainJourneyResponse(
        String departureStation,
        String arrivalStation,
        String departureCity,
        String arrivalCity,
        String departureCountryCode,
        String arrivalCountryCode,
        String departureTime,
        String arrivalTime,
        String carrier,
        String carrierLogoUrl,
        Integer durationMinutes,
        Integer stops,
        Integer ticketsLeft,
        Boolean splitTicket,
        List<TrainLegResponse> legs,
        List<TrainTransferResponse> transfers
) {
}
