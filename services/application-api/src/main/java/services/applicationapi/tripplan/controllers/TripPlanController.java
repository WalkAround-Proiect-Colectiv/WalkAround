package services.applicationapi.tripplan.controllers;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tripplan.dto.SaveTripPlanRequest;
import services.applicationapi.tripplan.dto.TripPlanResponse;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.tripplan.service.savedplan.SavedTripPlanService;

@RestController
@RequestMapping("/api/trip-plans")
public class TripPlanController {

    private final SavedTripPlanService tripPlans;

    public TripPlanController(SavedTripPlanService tripPlans) {
        this.tripPlans = tripPlans;
    }

    @PostMapping
    public ResponseEntity<TripPlanResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaveTripPlanRequest request
    ) {
        TripPlanResponse saved = tripPlans.create(currentUserId(jwt), request);
        URI location = URI.create("/api/trip-plans/" + saved.id());

        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripPlanResponse> get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(tripPlans.get(currentUserId(jwt), id));
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new TripPlanAuthenticationException();
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new TripPlanAuthenticationException();
        }
    }
}
