package services.applicationapi.recommendation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("recommendation")
public record RecommendationProperties(
        Integer defaultRadiusMeters,
        Integer maxRadiusMeters,
        Integer defaultLimit,
        Integer maxLimit,
        Duration cacheTtl,
        String poiDatabaseUrl,
        String poiDatabaseUsername,
        String poiDatabasePassword
) {
    public int resolvedDefaultRadiusMeters() {
        return defaultRadiusMeters != null && defaultRadiusMeters > 0 ? defaultRadiusMeters : 100;
    }

    public int resolvedMaxRadiusMeters() {
        return maxRadiusMeters != null && maxRadiusMeters > 0 ? maxRadiusMeters : 2000;
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
}
