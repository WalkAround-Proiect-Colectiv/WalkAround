package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record FlightSearchProperties(
        String serpApiKey,
        @DefaultValue("3") int flightSearchMaxResults,
        @DefaultValue("https://serpapi.com") URI serpApiBaseUrl,
        @DefaultValue("PT15S") Duration serpApiTimeout,
        @DefaultValue("7") int flightSearchMaxDateRangeDays
) {

    public FlightSearchProperties {
        if (flightSearchMaxResults < 1 || flightSearchMaxResults > 100) {
            throw new IllegalArgumentException("Flight search max results must be between 1 and 100");
        }

        if (!"https".equalsIgnoreCase(serpApiBaseUrl.getScheme())) {
            throw new IllegalArgumentException("SerpApi base URL must use HTTPS");
        }

        if (serpApiTimeout.isNegative() || serpApiTimeout.isZero()) {
            throw new IllegalArgumentException("SerpApi timeout must be positive");
        }

        if (flightSearchMaxDateRangeDays < 1 || flightSearchMaxDateRangeDays > 31) {
            throw new IllegalArgumentException("Flight search max date range must be between 1 and 31 days");
        }
    }
}
