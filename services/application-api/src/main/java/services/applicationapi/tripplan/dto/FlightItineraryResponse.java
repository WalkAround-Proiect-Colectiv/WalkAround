package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;
import java.util.List;

public record FlightItineraryResponse(
        TicketOption ticketOption,
        String currency,
        BigDecimal totalPrice,
        List<FlightSegmentResponse> outbound,
        List<FlightSegmentResponse> returning,
        List<String> viewUrls
) {
}
