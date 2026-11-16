package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.BusItineraryResponse;
import services.applicationapi.tripplan.dto.BusSearchRequest;
import services.applicationapi.tripplan.service.bussearch.BusSearchService;

@RestController
@RequestMapping("/api/trip-plans/bus-searches")
public class BusSearchController {

    private final BusSearchService service;

    public BusSearchController(BusSearchService service) {
        this.service = service;
    }

    @PostMapping
    public List<BusItineraryResponse> search(@Valid @RequestBody BusSearchRequest request) {
        return service.search(request);
    }
}
