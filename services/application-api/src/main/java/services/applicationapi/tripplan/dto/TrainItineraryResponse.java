package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;

public record TrainItineraryResponse(
        TicketOption ticketOption,
        String currency,
        BigDecimal totalPrice,
        TrainJourneyResponse outbound,
        TrainJourneyResponse returning,
        String source,
        BigDecimal originalTotalPrice,
        String originalCurrency,
        Boolean estimatedFare,
        Boolean partialCatalog,
        String outboundUrl,
        String returnUrl
) {
}
