package services.routingservice.dangerouszone.dto;

import java.util.List;

public record DangerousZoneHeatmapResponse(
        String h3Index,
        int intensity,
        List<String> topCategories
) {}