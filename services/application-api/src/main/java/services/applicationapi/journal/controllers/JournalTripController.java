package services.applicationapi.journal.controllers;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.journal.dto.*;
import services.applicationapi.journal.service.JournalTripService;

@RestController
@RequestMapping("/api/journal/trips")
public class JournalTripController {
    public record Settings(LocalDate today, String timeZone) {}
    private final JournalTripService trips;
    public JournalTripController(JournalTripService trips) { this.trips = trips; }

    @GetMapping("/settings")
    public ResponseEntity<Settings> settings() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Settings(trips.today(), "UTC")); }

    @GetMapping
    public ResponseEntity<JournalTripService.Page> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.list(owner(jwt), page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalTrip> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.get(owner(jwt), id));
    }

    @PostMapping
    public ResponseEntity<JournalTrip> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JournalTripRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(trips.create(owner(jwt), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JournalTrip> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody JournalTripRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(trips.update(owner(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        trips.delete(owner(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID owner(Jwt jwt) {
        try { return UUID.fromString(jwt.getSubject()); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in again."); }
    }
}