package services.applicationapi.tripplan.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.CountryHotlineResponse;
import services.applicationapi.tripplan.service.hotlines.CountryHotlineService;

@RestController
@RequestMapping("/api/trip-plans/countries")
public class CountryHotlineController {

    private final CountryHotlineService countryHotlineService;

    public CountryHotlineController(CountryHotlineService countryHotlineService) {
        this.countryHotlineService = countryHotlineService;
    }

    @GetMapping("/{countryCode}/hotlines")
    public ResponseEntity<CountryHotlineResponse> get(@PathVariable String countryCode) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(countryHotlineService.get(countryCode));
    }
}
