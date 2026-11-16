package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;

public record TrainItineraryResponse(
        TicketOption ticketOption,
        String currency,
        BigDecimal totalPrice,
        TrainJourneyResponse outbound,
        TrainJourneyResponse returning
) {
}
