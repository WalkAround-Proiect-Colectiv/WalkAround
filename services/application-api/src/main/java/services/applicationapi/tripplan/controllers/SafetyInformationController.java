package services.applicationapi.tripplan.controllers;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.DestinationSafetyResponse;
import services.applicationapi.tripplan.service.safety.SafetyInformationService;

@RestController
@RequestMapping("/api/trip-plans/cities")
public class SafetyInformationController {

    private final SafetyInformationService safetyInformationService;

    public SafetyInformationController(SafetyInformationService safetyInformationService) {
        this.safetyInformationService = safetyInformationService;
    }

    @GetMapping("/{city}/safety")
    public ResponseEntity<DestinationSafetyResponse> get(
            @PathVariable String city,
            @RequestParam String countryCode
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(safetyInformationService.get(city, countryCode));
    }
}
