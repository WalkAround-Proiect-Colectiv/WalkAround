package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import services.applicationapi.tripplan.model.GroupChatDocument;

public interface GroupChatDocumentRepository {
    Optional<GroupChatDocument> findByMessageId(UUID messageId);
    void insert(GroupChatDocument document);
}
