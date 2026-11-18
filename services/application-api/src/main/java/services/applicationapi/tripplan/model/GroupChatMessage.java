package services.applicationapi.tripplan.model;

import java.time.Instant;
import java.util.UUID;

public record GroupChatMessage(
        UUID id,
        UUID conversationId,
        long sequence,
        UUID senderId,
        String senderName,
        String body,
        Instant createdAt
) {
}
