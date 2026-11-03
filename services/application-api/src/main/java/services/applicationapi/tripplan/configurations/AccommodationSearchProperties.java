package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record AccommodationSearchProperties(
        String serpApiKey,
        @DefaultValue("3") int accommodationSearchMaxResults
) {

    public AccommodationSearchProperties {
        if (accommodationSearchMaxResults < 1 || accommodationSearchMaxResults > 100) {
            throw new IllegalArgumentException("Accommodation search max results must be between 1 and 100");
        }
    }
}
