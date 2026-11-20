package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record TrainSearchProperties(
        String scrappaApiKey,
        @DefaultValue("https://scrappa.co") URI scrappaBaseUrl,
        @DefaultValue("PT30S") Duration scrappaTimeout,
        @DefaultValue("3") int trainSearchMaxResults,
        @DefaultValue("7") int trainSearchMaxDateRangeDays,
        @DefaultValue("2") int trainSearchMaxArrivalOffsetDays,
        @DefaultValue("1") int trainSearchMaxPagesPerDate
) {

    public TrainSearchProperties {
        if (!"https".equalsIgnoreCase(scrappaBaseUrl.getScheme())) {
            throw new IllegalArgumentException("Scrappa base URL must use HTTPS");
        }
        if (scrappaTimeout.isZero() || scrappaTimeout.isNegative()) {
            throw new IllegalArgumentException("Scrappa timeout must be positive");
        }
        if (trainSearchMaxResults < 1 || trainSearchMaxResults > 20
                || trainSearchMaxDateRangeDays < 1 || trainSearchMaxDateRangeDays > 31
                || trainSearchMaxArrivalOffsetDays < 0 || trainSearchMaxArrivalOffsetDays > 7
                || trainSearchMaxPagesPerDate < 1 || trainSearchMaxPagesPerDate > 5) {
            throw new IllegalArgumentException("Train search limits are outside their allowed ranges");
        }
    }
}
