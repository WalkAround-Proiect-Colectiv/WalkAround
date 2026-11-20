package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.UUID;

public record GroupChatSocketTicket(String token, UUID conversationId, Instant expiresAt) {
}
