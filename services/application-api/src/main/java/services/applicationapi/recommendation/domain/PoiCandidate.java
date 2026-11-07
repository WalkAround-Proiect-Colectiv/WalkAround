package services.applicationapi.recommendation.domain;

public record PoiCandidate(
        String id,
        String name,
        String osmKind,
        String osmClass,
        double latitude,
        double longitude,
        double distanceMeters
) {
}
