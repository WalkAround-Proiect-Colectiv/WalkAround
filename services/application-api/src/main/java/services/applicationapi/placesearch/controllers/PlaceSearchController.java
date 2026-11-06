package services.applicationapi.placesearch.controllers;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.placesearch.dto.PlaceSearchResult;
import services.applicationapi.placesearch.service.PlaceSearchService;

@RestController
@RequestMapping("/api/places")
public class PlaceSearchController {

    private final PlaceSearchService service;

    public PlaceSearchController(PlaceSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public List<PlaceSearchResult> search(
            @RequestParam String q,
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "14") int zoom
    ) {
        return service.search(q, lat, lon, zoom);
    }
}
