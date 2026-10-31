package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.FlightItineraryResponse;
import services.applicationapi.tripplan.dto.FlightSearchRequest;
import services.applicationapi.tripplan.service.FlightSearchService;

@RestController
@RequestMapping("/api/trip-plans/flight-searches")
public class FlightSearchController {

    private final FlightSearchService flightSearchService;

    public FlightSearchController(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @PostMapping
    public List<FlightItineraryResponse> search(@Valid @RequestBody FlightSearchRequest request) {
        return flightSearchService.search(request);
    }
}
