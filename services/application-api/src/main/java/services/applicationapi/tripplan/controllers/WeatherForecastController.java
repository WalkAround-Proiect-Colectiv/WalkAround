package services.applicationapi.tripplan.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.DestinationWeatherResponse;
import services.applicationapi.tripplan.service.weather.WeatherForecastService;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class WeatherForecastController {

    private final WeatherForecastService weatherForecastService;

    public WeatherForecastController(WeatherForecastService weatherForecastService) {
        this.weatherForecastService = weatherForecastService;
    }

    @GetMapping("/{city}/weather")
    public ResponseEntity<DestinationWeatherResponse> get(
            @PathVariable String city,
            @RequestParam String countryCode
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(weatherForecastService.get(city, countryCode));
    }
}
