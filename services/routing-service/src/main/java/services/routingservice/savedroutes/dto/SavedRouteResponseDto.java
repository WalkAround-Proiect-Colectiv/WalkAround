package services.routingservice.savedroutes.dto;

import java.time.Instant;

public record SavedRouteResponseDto(
        Long id,
        Double startLat,
        Double startLng,
        Double endLat,
        Double endLng,
        Instant createdAt
) {}
