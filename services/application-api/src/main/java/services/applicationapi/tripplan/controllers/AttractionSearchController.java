package services.applicationapi.tripplan.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.PlaceRecommendationPageResponse;
import services.applicationapi.tripplan.service.attractionsearch.AttractionSearchService;
import services.applicationapi.tripplan.service.attractionsearch.PlaceCategory;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class AttractionSearchController {

    private final AttractionSearchService attractionSearchService;

    public AttractionSearchController(AttractionSearchService attractionSearchService) {
        this.attractionSearchService = attractionSearchService;
    }

    @GetMapping("/{city}/attractions")
    public ResponseEntity<PlaceRecommendationPageResponse> attractions(
            @PathVariable String city,
            @RequestParam String countryCode,
            @RequestParam(defaultValue = "0") int page
    ) {
        return search(city, countryCode, page, PlaceCategory.ATTRACTIONS);
    }

    @GetMapping("/{city}/restaurants")
    public ResponseEntity<PlaceRecommendationPageResponse> restaurants(
            @PathVariable String city,
            @RequestParam String countryCode,
            @RequestParam(defaultValue = "0") int page
    ) {
        return search(city, countryCode, page, PlaceCategory.RESTAURANTS);
    }

    @GetMapping("/{city}/museums")
    public ResponseEntity<PlaceRecommendationPageResponse> museums(
            @PathVariable String city,
            @RequestParam String countryCode,
            @RequestParam(defaultValue = "0") int page
    ) {
        return search(city, countryCode, page, PlaceCategory.MUSEUMS);
    }

    @GetMapping("/{city}/coffee-shops")
    public ResponseEntity<PlaceRecommendationPageResponse> coffeeShops(
            @PathVariable String city,
            @RequestParam String countryCode,
            @RequestParam(defaultValue = "0") int page
    ) {
        return search(city, countryCode, page, PlaceCategory.COFFEE_SHOPS);
    }

    private ResponseEntity<PlaceRecommendationPageResponse> search(
            String city, String countryCode, int page, PlaceCategory category
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(attractionSearchService.search(city, countryCode, page, category));
    }
}
