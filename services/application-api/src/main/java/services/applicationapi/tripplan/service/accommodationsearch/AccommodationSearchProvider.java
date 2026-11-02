package services.applicationapi.tripplan.service.accommodationsearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AccommodationSearchProvider {

    List<AccommodationOffer> search(String destination, LocalDate checkInDate, LocalDate checkOutDate);

    AccommodationDetails details(String propertyToken, LocalDate checkInDate, LocalDate checkOutDate);

    record AccommodationOffer(
            String name,
            String propertyType,
            BigDecimal totalPrice,
            String propertyWebsiteUrl,
            String propertyToken
    ) {
    }

    record AccommodationDetails(
            BigDecimal totalPrice,
            String description,
            String address,
            BigDecimal overallRating,
            Integer reviews,
            List<String> amenities,
            List<String> roomTypes,
            List<BookingOption> bookingOptions
    ) {
    }

    record BookingOption(
            String source,
            BigDecimal totalPrice,
            String bookingUrl
    ) {
    }
}
