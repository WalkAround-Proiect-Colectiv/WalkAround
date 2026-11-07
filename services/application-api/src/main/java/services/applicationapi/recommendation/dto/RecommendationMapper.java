package services.applicationapi.recommendation.dto;

import java.util.List;
import org.springframework.stereotype.Component;
import services.applicationapi.recommendation.domain.ActivityRecommendationResult;
import services.applicationapi.recommendation.domain.RankedActivityRecommendation;

@Component
public class RecommendationMapper {

    public RecommendationResponseDto toResponseDto(ActivityRecommendationResult result) {
        List<ActivityRecommendationDto> items = result.recommendations().stream()
                .map(this::toItemDto)
                .toList();

        return new RecommendationResponseDto(
                result.weatherBucket().name(),
                result.temperatureC(),
                result.weatherSummary(),
                result.degraded(),
                items,
                result.generatedAt()
        );
    }

    public ActivityRecommendationDto toItemDto(RankedActivityRecommendation item) {
        return new ActivityRecommendationDto(
                item.id(),
                item.name(),
                item.category(),
                item.categoryDisplayName(),
                item.indoor(),
                item.latitude(),
                item.longitude(),
                item.distanceMeters(),
                item.reason(),
                item.openingHours(),
                item.address()
        );
    }
}
