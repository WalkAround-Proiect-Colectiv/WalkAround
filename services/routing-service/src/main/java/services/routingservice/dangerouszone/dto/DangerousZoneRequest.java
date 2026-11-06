package services.routingservice.dangerouszone.dto;

import jakarta.validation.constraints.NotNull;
import services.routingservice.dangerouszone.model.DangerousZoneCategory;


import java.time.LocalTime;

public record DangerousZoneRequest(
        @NotNull Double lat,
        @NotNull Double lng,
        @NotNull DangerousZoneCategory category,
        String description,
        boolean is24_7,
        LocalTime startTime,
        LocalTime endTime
) {}