package services.applicationapi.placesearch.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.place-search")
public record PlaceSearchProperties(
        @DefaultValue("photon") String provider,
        @DefaultValue("https://photon.komoot.io") URI baseUrl,
        @DefaultValue("PT10S") Duration timeout,
        @DefaultValue("5") int maxResults
) {

    public PlaceSearchProperties {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("PLACE_SEARCH_PROVIDER must be configured");
        }
        if (!"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Place search provider URL must use HTTPS");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Place search timeout must be positive");
        }
        if (maxResults < 1 || maxResults > 10) {
            throw new IllegalArgumentException("Place search max results must be between 1 and 10");
        }
    }
}
