package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record CityAutocompleteProperties(
        String geonamesUsername,
        @DefaultValue("10") int cityAutocompleteMaxResults,
        @DefaultValue("https://api.geonames.org") URI geonamesBaseUrl,
        @DefaultValue("PT3S") Duration geonamesTimeout
) {

    public CityAutocompleteProperties {
        if (geonamesUsername == null || geonamesUsername.isBlank()) {
            throw new IllegalArgumentException("GEONAMES_USERNAME must be configured");
        }

        if (cityAutocompleteMaxResults < 1 || cityAutocompleteMaxResults > 1000) {
            throw new IllegalArgumentException("City autocomplete max results must be between 1 and 1000");
        }

        if (!"https".equalsIgnoreCase(geonamesBaseUrl.getScheme())) {
            throw new IllegalArgumentException("GeoNames base URL must use HTTPS");
        }

        if (geonamesTimeout.isNegative() || geonamesTimeout.isZero()) {
            throw new IllegalArgumentException("GeoNames timeout must be positive");
        }
    }
}
