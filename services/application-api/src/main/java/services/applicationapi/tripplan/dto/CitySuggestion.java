package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;
import services.applicationapi.tripplan.CityNameNormalizer;

public record CitySuggestion(
        String name,
        String country,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String lookupName
) {

    public CitySuggestion(
            String name,
            String country,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this(
                name,
                country,
                countryCode,
                latitude,
                longitude,
                CityNameNormalizer.toLookupName(name)
        );
    }
}
