package services.applicationapi.tripplan.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import services.applicationapi.tripplan.model.GroupChatMessage;

public interface GroupChatRepository {

    Optional<GroupChatMessage> findById(UUID messageId);

    default GroupChatMessage insert(UUID messageId, UUID conversationId, UUID senderId,
                                    String body, String messageType, Instant createdAt) {
        return insert(messageId, conversationId, senderId, body, messageType, createdAt, null);
    }

    GroupChatMessage insert(UUID messageId, UUID conversationId, UUID senderId,
                            String body, String messageType, Instant createdAt, UUID replyToMessageId);

    List<GroupChatMessage> latest(UUID conversationId, int limit);

    List<GroupChatMessage> after(UUID conversationId, long sequence, int limit);

    List<GroupChatMessage> before(UUID conversationId, long sequence, int limit);

    long eventCursor(UUID conversationId);

    long eventSequence(UUID eventId);

    List<EventReference> eventsAfter(UUID conversationId, long eventSequence, int limit);

    long nextEventSequence(UUID conversationId);

    void enqueueEvent(UUID eventId, UUID messageId, UUID conversationId, long sequence,
                      long eventSequence, String payload, Instant createdAt);

    void setPayload(UUID eventId, String payload);

    List<PendingEvent> lockPending(int limit);

    void markPublished(UUID eventId);

    void defer(UUID eventId, Instant nextAttemptAt);

    record EventReference(long eventSequence, UUID messageId) {
    }

    record PendingEvent(GroupChatMessage message, UUID eventId, long eventSequence,
                        String payload, int attempts) {
    }
}
