package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;

public record BusItineraryResponse(
        TicketOption ticketOption,
        String currency,
        BigDecimal totalPrice,
        BusJourneyResponse outbound,
        BusJourneyResponse returning
) {
}
