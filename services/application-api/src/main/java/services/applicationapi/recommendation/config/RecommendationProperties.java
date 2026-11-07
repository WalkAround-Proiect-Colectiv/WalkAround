package services.applicationapi.recommendation.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("recommendation")
public record RecommendationProperties(
        Integer defaultRadiusMeters,
        Integer maxRadiusMeters,
        Integer defaultLimit,
        Integer maxLimit,
        Duration cacheTtl,
        URI openMeteoBaseUrl,
        Duration openMeteoTimeout,
        String poiDatabaseUrl,
        String poiDatabaseUsername,
        String poiDatabasePassword
) {
    public int resolvedDefaultRadiusMeters() {
        return defaultRadiusMeters != null && defaultRadiusMeters > 0 ? defaultRadiusMeters : 2000;
    }

    public int resolvedMaxRadiusMeters() {
        return maxRadiusMeters != null && maxRadiusMeters > 0 ? maxRadiusMeters : 5000;
    }

    public int resolvedDefaultLimit() {
        return defaultLimit != null && defaultLimit > 0 ? defaultLimit : 15;
    }

    public int resolvedMaxLimit() {
        return maxLimit != null && maxLimit > 0 ? maxLimit : 50;
    }

    public Duration resolvedCacheTtl() {
        return cacheTtl != null && !cacheTtl.isNegative() && !cacheTtl.isZero()
                ? cacheTtl
                : Duration.ofMinutes(15);
    }

    public URI resolvedOpenMeteoBaseUrl() {
        return openMeteoBaseUrl != null
                ? openMeteoBaseUrl
                : URI.create("https://api.open-meteo.com");
    }

    public Duration resolvedOpenMeteoTimeout() {
        return openMeteoTimeout != null && !openMeteoTimeout.isNegative() && !openMeteoTimeout.isZero()
                ? openMeteoTimeout
                : Duration.ofSeconds(5);
    }
}
