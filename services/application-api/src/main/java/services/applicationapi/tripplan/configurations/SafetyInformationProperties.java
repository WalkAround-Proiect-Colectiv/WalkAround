package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record SafetyInformationProperties(
        String travelRiskApiKey,
        @DefaultValue("https://api.travelriskapi.com") URI travelRiskApiBaseUrl,
        @DefaultValue("PT8S") Duration travelRiskApiTimeout,
        @DefaultValue("100") int safetyNearbyRadiusKm,
        @DefaultValue("10") int safetyMaxResults
) {
    public SafetyInformationProperties {
        if (!"https".equalsIgnoreCase(travelRiskApiBaseUrl.getScheme())
                || travelRiskApiBaseUrl.getHost() == null
                || travelRiskApiBaseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Travel Risk API base URL must be an HTTPS host");
        }
        if (travelRiskApiTimeout.isNegative() || travelRiskApiTimeout.isZero()) {
            throw new IllegalArgumentException("Travel Risk API timeout must be positive");
        }
        if (safetyNearbyRadiusKm < 1 || safetyNearbyRadiusKm > 500) {
            throw new IllegalArgumentException("Safety nearby radius must be between 1 and 500 km");
        }
        if (safetyMaxResults < 1 || safetyMaxResults > 50) {
            throw new IllegalArgumentException("Safety result limit must be between 1 and 50");
        }
    }
}
