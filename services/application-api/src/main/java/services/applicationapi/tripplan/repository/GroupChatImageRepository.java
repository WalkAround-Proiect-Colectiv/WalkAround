package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import services.applicationapi.tripplan.model.GroupChatImage;

public interface GroupChatImageRepository {

    Optional<GroupChatImage> findByMessageId(UUID messageId);

    void insert(GroupChatImage image);
}
