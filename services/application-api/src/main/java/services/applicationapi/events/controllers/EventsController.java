package services.applicationapi.events.controllers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.events.dto.EventDetailsMapper;
import services.applicationapi.events.dto.EventDetailsResponse;
import services.applicationapi.events.dto.EventResponse;
import services.applicationapi.events.dto.InterestRequest;
import services.applicationapi.events.dto.InterestResponse;
import services.applicationapi.events.dto.LocalityResponse;
import services.applicationapi.events.dto.NearbyEventsResponse;
import services.applicationapi.events.service.EventDetailsService;
import services.applicationapi.events.service.EventInterestService;
import services.applicationapi.events.service.EventsService;
import services.applicationapi.events.service.exceptions.EventsAuthenticationException;

/** Upcoming events near the user, marked events and their reminders. All endpoints require sign-in. */
@RestController
@Validated
@RequestMapping("/api/events")
public class EventsController {

    static final String ATTRIBUTION = "Event data from Google Events via SerpApi. Locations © OpenStreetMap contributors.";

    private final EventsService events;
    private final EventInterestService interests;
    private final EventDetailsService eventDetailsService;
    private final EventDetailsMapper eventDetailsMapper;

    public EventsController(
            EventsService events,
            EventInterestService interests,
            EventDetailsService eventDetailsService,
            EventDetailsMapper eventDetailsMapper
    ) {
        this.events = events;
        this.interests = interests;
        this.eventDetailsService = eventDetailsService;
        this.eventDetailsMapper = eventDetailsMapper;
    }

    @GetMapping("/nearby")
    public ResponseEntity<NearbyEventsResponse> nearby(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng
    ) {
        UUID userId = currentUserId(jwt);
        EventsService.NearbyEvents nearby = events.nearby(lat, lng);
        Map<String, InterestResponse> marked = interests.upcoming(userId).stream()
                .map(InterestResponse::of)
                .collect(Collectors.toMap(InterestResponse::eventKey, Function.identity(), (a, b) -> a));
        LocalityResponse locality = nearby.locality()
                .map(value -> new LocalityResponse(value.key(), value.name(), value.county()))
                .orElse(null);
        List<EventResponse> list = nearby.events().stream()
                .map(event -> EventResponse.of(event, locality.key(), marked.get(event.key())))
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new NearbyEventsResponse(locality, list, nearby.fetchedAt(), nearby.stale(), ATTRIBUTION));
    }

    @GetMapping("/details")
    public ResponseEntity<EventDetailsResponse> details(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @NotBlank @Size(max = 200) String localityKey,
            @RequestParam @NotBlank @Size(max = 64) String eventKey
    ) {
        UUID userId = currentUserId(jwt);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(eventDetailsMapper.toResponse(
                        eventDetailsService.getDetails(userId, localityKey, eventKey),
                        ATTRIBUTION
                ));
    }

    @GetMapping("/interests")
    public ResponseEntity<List<InterestResponse>> interests(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(interests.upcoming(currentUserId(jwt)).stream().map(InterestResponse::of).toList());
    }

    @PutMapping("/interests")
    public ResponseEntity<InterestResponse> mark(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InterestRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(InterestResponse.of(interests.mark(
                        currentUserId(jwt), request.localityKey(), request.eventKey(), request.remindBeforeMinutes())));
    }

    @DeleteMapping("/interests/{eventKey}")
    public ResponseEntity<Void> unmark(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 64) String eventKey
    ) {
        interests.unmark(currentUserId(jwt), eventKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reminders/unseen")
    public ResponseEntity<List<InterestResponse>> unseenReminders(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(interests.unseenReminders(currentUserId(jwt)).stream().map(InterestResponse::of).toList());
    }

    @PostMapping("/interests/{eventKey}/reminder-seen")
    public ResponseEntity<Void> reminderSeen(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 64) String eventKey
    ) {
        interests.markReminderSeen(currentUserId(jwt), eventKey);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new EventsAuthenticationException();
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new EventsAuthenticationException();
        }
    }
}
