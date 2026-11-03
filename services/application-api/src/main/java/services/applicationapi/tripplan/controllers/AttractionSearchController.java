package services.applicationapi.tripplan.controllers;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.AttractionResponse;
import services.applicationapi.tripplan.service.attractionsearch.AttractionSearchService;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class AttractionSearchController {

    private final AttractionSearchService attractionSearchService;

    public AttractionSearchController(AttractionSearchService attractionSearchService) {
        this.attractionSearchService = attractionSearchService;
    }

    @GetMapping("/{city}/attractions")
    public ResponseEntity<List<AttractionResponse>> search(
            @PathVariable String city,
            @RequestParam String countryCode
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(attractionSearchService.search(city, countryCode));
    }
}
