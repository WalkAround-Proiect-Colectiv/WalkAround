package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.UUID;

public record TripGroupInviteSummary(UUID id, Instant createdAt, Instant expiresAt,
                                     Instant usedAt, Instant revokedAt) {
}
