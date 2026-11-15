package services.applicationapi.recommendation.service;

import services.applicationapi.recommendation.dto.RecommendationRequest;
import services.applicationapi.recommendation.dto.RecommendationResponseDto;

public interface ActivityRecommendationService {

    RecommendationResponseDto getRecommendations(RecommendationRequest request);
}
