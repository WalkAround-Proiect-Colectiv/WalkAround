package services.applicationapi.tripplan.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import services.applicationapi.tripplan.dto.GroupChatPollResponse;

public interface GroupChatPollRepository {

    void create(UUID messageId, List<String> labels);

    Optional<GroupChatPollResponse> snapshot(UUID messageId, UUID viewerId);

    boolean vote(UUID messageId, UUID userId, UUID optionId, Instant now);

    boolean close(UUID messageId, Instant now);
}
