package services.routingservice.dangerouszone.controllers;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import services.routingservice.dangerouszone.dto.DangerousZoneHeatmapResponse;
import services.routingservice.dangerouszone.dto.DangerousZoneRequest;
import services.routingservice.dangerouszone.model.DangerousZone;
import services.routingservice.dangerouszone.service.DangerousZoneService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dangerous-zones")
public class DangerousZoneController
{
    private final DangerousZoneService service;
    public DangerousZoneController(DangerousZoneService service)
    {
        this.service = service;
    }
    @PostMapping
    public ResponseEntity<Void> reportZone(@Valid @RequestBody DangerousZoneRequest request, @AuthenticationPrincipal Jwt jwt)
    {
        UUID userId = UUID.fromString(jwt.getSubject());
        service.reportZone(request, userId);
        return ResponseEntity.ok().build();
    }
    @GetMapping
    public ResponseEntity<List<DangerousZoneHeatmapResponse>> getHeatmap() {
        return ResponseEntity.ok(service.getHeatmapData());
    }
}