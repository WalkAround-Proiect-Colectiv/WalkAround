package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record TripPlanResponse(
        UUID id,
        String title,
        String description,
        String originAirport,
        String departureCity,
        String departureCountryCode,
        String destinationCity,
        String countryCode,
        LocalDateTime departureFrom,
        LocalDateTime departureUntil,
        int stayingNights,
        TicketOption ticketOption,
        BigDecimal flightBudget,
        BigDecimal accommodationBudget,
        Instant createdAt
) {
}
