package services.applicationapi.tripplan.dto;

import java.time.Instant;

public record TripGroupInvitePreview(String groupName, Instant expiresAt, boolean pictureAvailable) {
}
