package services.applicationapi.recommendation.dto;

import java.time.Instant;
import java.util.List;

public record RecommendationResponseDto(
        String weatherBucket,
        Double temperatureC,
        String weatherSummary,
        boolean degraded,
        List<ActivityRecommendationDto> recommendations,
        Instant generatedAt
) {
}
