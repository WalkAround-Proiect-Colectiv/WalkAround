package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;

public record CitySuggestion(
        String name,
        String country,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
