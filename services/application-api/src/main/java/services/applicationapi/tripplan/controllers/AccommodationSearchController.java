package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.AccommodationOfferResponse;
import services.applicationapi.tripplan.dto.AccommodationSearchRequest;
import services.applicationapi.tripplan.service.accommodationsearch.AccommodationSearchService;

@RestController
@RequestMapping("/api/trip-plans/accommodation-searches")
public class AccommodationSearchController {

    private final AccommodationSearchService accommodationSearchService;

    public AccommodationSearchController(AccommodationSearchService accommodationSearchService) {
        this.accommodationSearchService = accommodationSearchService;
    }

    @PostMapping
    public List<AccommodationOfferResponse> search(@Valid @RequestBody AccommodationSearchRequest request) {
        return accommodationSearchService.search(request);
    }
}
