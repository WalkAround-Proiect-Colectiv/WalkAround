package services.applicationapi.recommendation.domain;

import java.time.Instant;
import java.util.List;

public record ActivityRecommendationResult(
        WeatherBucket weatherBucket,
        String weatherSummary,
        boolean degraded,
        List<RankedActivityRecommendation> recommendations,
        Instant generatedAt
) {
}
