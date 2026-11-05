package services.applicationapi.recommendation.controller;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.recommendation.dto.ActivityRecommendationQueryDto;
import services.applicationapi.recommendation.dto.RecommendationRequest;
import services.applicationapi.recommendation.dto.RecommendationResponseDto;
import services.applicationapi.recommendation.service.ActivityRecommendationService;

@RestController
@RequestMapping("/api/v1/recommendations/activities")
public class ActivityRecommendationController {

    private final ActivityRecommendationService recommendationService;

    public ActivityRecommendationController(ActivityRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public ResponseEntity<RecommendationResponseDto> getRecommendations(
            @Valid @ModelAttribute ActivityRecommendationQueryDto query
    ) {
        RecommendationResponseDto response = recommendationService.getRecommendations(query.toRecommendationRequest());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping
    public ResponseEntity<RecommendationResponseDto> postRecommendations(
            @Valid @RequestBody RecommendationRequest request
    ) {
        RecommendationResponseDto response = recommendationService.getRecommendations(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
