package services.applicationapi.tripplan.controllers;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.AirportResponse;
import services.applicationapi.tripplan.service.CityAirportService;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class CityAirportController {

    private final CityAirportService cityAirportService;

    public CityAirportController(CityAirportService cityAirportService) {
        this.cityAirportService = cityAirportService;
    }

    @GetMapping("/{city}/airports")
    public List<AirportResponse> findAirports(
            @PathVariable String city,
            @RequestParam String countryCode
    ) {
        return cityAirportService.findAirports(city, countryCode);
    }
}
