package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.UUID;

public record TripGroupResponse(
        UUID id,
        String name,
        String description,
        String memberRole,
        String pictureUrl,
        Instant createdAt
) {
}
