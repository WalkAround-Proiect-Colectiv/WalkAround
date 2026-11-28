package services.applicationapi.tripplan.dto;

import services.applicationapi.tripplan.model.GroupChatDocumentMetadata;
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
        Instant createdAt,
        String messageType,
        GroupChatPollResponse poll,
        GroupChatImageResponse image,
        GroupChatReplyResponse replyTo,
        GroupChatDocumentMetadata document
) {
    public static GroupChatMessageResponse from(GroupChatMessage message, GroupChatPollResponse poll,
                                                GroupChatImageResponse image) {
        return from(message, poll, image, null);
    }

    public static GroupChatMessageResponse from(GroupChatMessage message, GroupChatPollResponse poll,
                                                GroupChatImageResponse image, GroupChatReplyResponse replyTo) {
        return new GroupChatMessageResponse(
                message.id(), message.conversationId(), message.sequence(), message.senderId(),
                message.senderName(), message.body(), message.createdAt(), message.messageType(), poll, image, replyTo, message.document());
    }
}
