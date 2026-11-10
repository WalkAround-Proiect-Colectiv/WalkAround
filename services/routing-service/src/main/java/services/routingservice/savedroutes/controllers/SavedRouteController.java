package services.routingservice.savedroutes.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import services.routingservice.savedroutes.dto.SavedRouteRequestDto;
import services.routingservice.savedroutes.dto.SavedRouteResponseDto;
import services.routingservice.savedroutes.service.SavedRouteService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/saved-routes")
public class SavedRouteController
{
    private final SavedRouteService savedRouteService;
    public SavedRouteController(SavedRouteService savedRouteService)
    {
        this.savedRouteService = savedRouteService;
    }
    @PostMapping
    public ResponseEntity<SavedRouteResponseDto> saveRoute(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody SavedRouteRequestDto request)
    {
        String userId = jwt.getSubject();
        log.info("Received request to save route for user: {}", userId);
        SavedRouteResponseDto response = savedRouteService.saveRoute(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{routeId}")
    public ResponseEntity<SavedRouteResponseDto> updateRoute(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long routeId,
            @RequestBody SavedRouteRequestDto request)
    {
        String userId = jwt.getSubject();
        log.info("Received request to update route {} for user: {}", routeId, userId);
        SavedRouteResponseDto response = savedRouteService.updateRoute(userId, routeId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SavedRouteResponseDto>> getUserRoutes(@AuthenticationPrincipal Jwt jwt)
    {
        String userId = jwt.getSubject();
        log.info("Received request to fetch routes for user: {}", userId);
        List<SavedRouteResponseDto> routes = savedRouteService.getUserSavedRoutes(userId);
        return ResponseEntity.ok(routes);
    }

    @DeleteMapping("/{routeId}")
    public ResponseEntity<Void> deleteRoute(@AuthenticationPrincipal Jwt jwt, @PathVariable Long routeId)
    {
        String userId = jwt.getSubject();
        log.info("Received request to delete route {} for user: {}", routeId, userId);
        savedRouteService.deleteRoute(userId, routeId);
        return ResponseEntity.noContent().build();
    }
}
