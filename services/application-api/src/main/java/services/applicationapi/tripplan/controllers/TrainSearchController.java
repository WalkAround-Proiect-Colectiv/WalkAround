package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.TrainItineraryResponse;
import services.applicationapi.tripplan.dto.TrainSearchRequest;
import services.applicationapi.tripplan.service.trainsearch.TrainSearchService;

@RestController
@RequestMapping("/api/trip-plans/train-searches")
public class TrainSearchController {

    private final TrainSearchService service;

    public TrainSearchController(TrainSearchService service) {
        this.service = service;
    }

    @PostMapping
    public List<TrainItineraryResponse> search(@Valid @RequestBody TrainSearchRequest request) {
        return service.search(request);
    }
}
