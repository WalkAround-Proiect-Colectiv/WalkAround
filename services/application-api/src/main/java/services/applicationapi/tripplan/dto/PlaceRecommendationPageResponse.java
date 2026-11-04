package services.applicationapi.tripplan.dto;

import java.util.List;

public record PlaceRecommendationPageResponse(List<PlaceRecommendationResponse> items, Integer nextPage) {
}
