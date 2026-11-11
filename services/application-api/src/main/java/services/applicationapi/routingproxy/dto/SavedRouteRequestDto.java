package services.applicationapi.routingproxy.dto;

import jakarta.validation.constraints.NotNull;

public record SavedRouteRequestDto(
        @NotNull Double startLat,
        @NotNull Double startLng,
        @NotNull Double endLat,
        @NotNull Double endLng
) {}
