package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record TripPlanResponse(
        UUID id,
        UUID groupId,
        String title,
        String description,
        TransportMode transportMode,
        String originAirport,
        String departureCity,
        String departureCountryCode,
        String destinationCity,
        String countryCode,
        LocalDateTime departureFrom,
        LocalDateTime departureUntil,
        int stayingNights,
        int adults,
        TicketOption ticketOption,
        BigDecimal transportBudget,
        BigDecimal accommodationBudget,
        boolean current,
        Instant createdAt
) {
}
