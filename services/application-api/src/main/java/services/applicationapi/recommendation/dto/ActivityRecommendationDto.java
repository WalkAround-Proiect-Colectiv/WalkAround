package services.applicationapi.recommendation.dto;

public record ActivityRecommendationDto(
        String id,
        String name,
        String category,
        String categoryDisplayName,
        boolean indoor,
        double latitude,
        double longitude,
        int distanceMeters,
        String reason,
        String openingHours,
        String address
) {
}
