package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.List;

public record DestinationSafetyResponse(
        String city,
        String country,
        String countryCode,
        int nearbyRadiusKm,
        Instant checkedAt,
        Advisory advisory,
        List<Alert> alerts,
        List<Conflict> conflicts,
        boolean alertsPartial,
        boolean conflictsPartial
) {
    public record Advisory(Integer maxLevel, String summary, List<Source> sources) {
    }

    public record Source(String agency, Integer level, String summary, String url, String updatedAt) {
    }

    public record Alert(
            Long id,
            String type,
            String severity,
            String location,
            String description,
            String eventDate,
            String source,
            double distanceKm
    ) {
    }

    public record Conflict(
            Long id,
            String severity,
            String location,
            String description,
            String eventDate,
            String sourceUrl,
            double distanceKm
    ) {
    }
}
