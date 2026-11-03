package services.applicationapi.tripplan.service.attractionsearch;

import services.applicationapi.tripplan.dto.PlaceRecommendationPageResponse;

public interface AttractionSearchService {

    PlaceRecommendationPageResponse search(String city, String countryCode, int page, PlaceCategory category);
}
