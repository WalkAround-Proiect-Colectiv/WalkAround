package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AccommodationOfferResponse(
        String name,
        String propertyType,
        BigDecimal totalPrice,
        String currency,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String propertyWebsiteUrl,
        String description,
        String address,
        BigDecimal overallRating,
        Integer reviews,
        List<String> amenities,
        List<String> roomTypes,
        List<AccommodationBookingOptionResponse> bookingOptions
) {
}
