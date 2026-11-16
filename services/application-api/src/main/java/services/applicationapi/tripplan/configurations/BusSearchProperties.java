package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record BusSearchProperties(
        String scrappaApiKey,
        @DefaultValue("https://scrappa.co") URI scrappaBaseUrl,
        @DefaultValue("PT30S") Duration scrappaTimeout,
        @DefaultValue("3") int busSearchMaxResults,
        @DefaultValue("7") int busSearchMaxDateRangeDays,
        @DefaultValue("2") int busSearchMaxArrivalOffsetDays,
        @DefaultValue("1") int busSearchMaxPagesPerDate
) {

    public BusSearchProperties {
        if (!"https".equalsIgnoreCase(scrappaBaseUrl.getScheme())) {
            throw new IllegalArgumentException("Scrappa base URL must use HTTPS");
        }
        if (scrappaTimeout.isNegative() || scrappaTimeout.isZero()) {
            throw new IllegalArgumentException("Scrappa timeout must be positive");
        }
        if (busSearchMaxResults < 1 || busSearchMaxResults > 20
                || busSearchMaxDateRangeDays < 1 || busSearchMaxDateRangeDays > 31
                || busSearchMaxArrivalOffsetDays < 0 || busSearchMaxArrivalOffsetDays > 7
                || busSearchMaxPagesPerDate < 1 || busSearchMaxPagesPerDate > 5) {
            throw new IllegalArgumentException("Bus search limits are outside their allowed ranges");
        }
    }
}
