package services.applicationapi.tripplan.dto;

import java.math.BigDecimal;

public record PlaceRecommendationResponse(
        String name,
        String category,
        String address,
        String description,
        BigDecimal rating,
        Integer reviews,
        Double latitude,
        Double longitude,
        String mapsUrl,
        String imageUrl,
        String websiteUrl
) {
}
