package services.applicationapi.preferences.controllers;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import services.applicationapi.preferences.dto.*;
import services.applicationapi.preferences.service.PreferencesService;

@RestController
@RequestMapping("/api/users/me/preferences")
public class PreferencesController {
    private final PreferencesService preferences;
    public PreferencesController(PreferencesService preferences) { this.preferences = preferences; }

    @GetMapping
    ResponseEntity<PreferencesResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return response(preferences.get(UUID.fromString(jwt.getSubject())));
    }

    @PutMapping
    ResponseEntity<PreferencesResponse> update(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody UpdatePreferencesRequest request) {
        return response(preferences.update(UUID.fromString(jwt.getSubject()), request));
    }

    private ResponseEntity<PreferencesResponse> response(PreferencesResponse preferences) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preferences);
    }
}
