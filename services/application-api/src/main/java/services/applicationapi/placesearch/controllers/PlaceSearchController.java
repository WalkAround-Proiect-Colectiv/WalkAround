package services.applicationapi.placesearch.controllers;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.placesearch.dto.PlaceSearchResult;
import services.applicationapi.placesearch.dto.NearbyPlacePage;
import services.applicationapi.placesearch.service.NearbyPlaceService;
import services.applicationapi.placesearch.service.PlaceSearchService;

@RestController
@RequestMapping("/api/places")
public class PlaceSearchController {

    private final PlaceSearchService service;
    private final NearbyPlaceService nearbyService;

    public PlaceSearchController(PlaceSearchService service, NearbyPlaceService nearbyService) {
        this.service = service;
        this.nearbyService = nearbyService;
    }

    @GetMapping("/nearby")
    public ResponseEntity<NearbyPlacePage> nearby(
            @RequestParam String type,
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "3000") int radiusMeters,
            @RequestParam(defaultValue = "0") int page
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(nearbyService.search(type, lat, lon, radiusMeters, page));
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

    @GetMapping("/reverse")
    public PlaceSearchResult reverse(
            @RequestParam double lat,
            @RequestParam double lon
    ) {
        return service.reverse(lat, lon);
    }
}
