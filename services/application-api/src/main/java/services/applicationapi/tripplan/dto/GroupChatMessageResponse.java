package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.UUID;
import services.applicationapi.tripplan.model.GroupChatMessage;

public record GroupChatMessageResponse(
        UUID id,
        UUID conversationId,
        long sequence,
        UUID senderId,
        String senderName,
        String body,
        Instant createdAt
) {
    public static GroupChatMessageResponse from(GroupChatMessage message) {
        return new GroupChatMessageResponse(
                message.id(), message.conversationId(), message.sequence(), message.senderId(),
                message.senderName(), message.body(), message.createdAt());
    }
}
