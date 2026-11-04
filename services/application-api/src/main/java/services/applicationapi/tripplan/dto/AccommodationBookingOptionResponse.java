package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;

public record AccommodationBookingOptionResponse(
        String source,
        BigDecimal totalPrice,
        String currency,
        String bookingUrl
) {
}
