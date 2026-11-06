package services.applicationapi.tripplan.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.HistoricalWeatherResponse;
import services.applicationapi.tripplan.service.weather.HistoricalWeatherService;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class HistoricalWeatherController {

    private final HistoricalWeatherService service;

    public HistoricalWeatherController(HistoricalWeatherService service) {
        this.service = service;
    }

    @GetMapping("/{city}/historical-weather")
    public ResponseEntity<HistoricalWeatherResponse> get(
            @PathVariable String city,
            @RequestParam String countryCode,
            @RequestParam String checkInDate,
            @RequestParam String stayingNights
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.get(city, countryCode, checkInDate, stayingNights));
    }
}
