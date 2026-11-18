package services.applicationapi.tripplan.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import services.applicationapi.tripplan.model.GroupChatMessage;

public interface GroupChatRepository {

    Optional<GroupChatMessage> findById(UUID messageId);

    GroupChatMessage insert(UUID messageId, UUID conversationId, UUID senderId, String body, Instant createdAt);

    List<GroupChatMessage> latest(UUID conversationId, int limit);

    List<GroupChatMessage> after(UUID conversationId, long sequence, int limit);

    List<GroupChatMessage> before(UUID conversationId, long sequence, int limit);

    List<PendingMessage> lockPending(int limit);

    void markPublished(UUID messageId);

    void defer(UUID messageId, Instant nextAttemptAt);

    record PendingMessage(GroupChatMessage message, int attempts) {
    }
}
