package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.UUID;

public record TripGroupInviteResponse(UUID id, String token, Instant expiresAt) {
}
