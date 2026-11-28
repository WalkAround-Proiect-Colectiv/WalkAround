package services.applicationapi.tripplan.dto;

import services.applicationapi.tripplan.model.GroupRole;

import java.time.Instant;
import java.util.UUID;

public record TripGroupResponse(
        UUID id,
        String name,
        String description,
        GroupRole memberRole,
        String pictureUrl,
        Instant createdAt
) {
}
