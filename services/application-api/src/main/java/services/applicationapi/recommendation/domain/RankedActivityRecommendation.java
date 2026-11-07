package services.applicationapi.recommendation.domain;

public record RankedActivityRecommendation(
        String id,
        String name,
        String category,
        String categoryDisplayName,
        boolean indoor,
        double latitude,
        double longitude,
        int distanceMeters,
        double score,
        String reason,
        String openingHours,
        String address
) {
}
