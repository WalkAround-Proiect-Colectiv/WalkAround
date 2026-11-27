package services.applicationapi.tripplan.service.groupchat;

import java.util.UUID;

public interface GroupChatDocumentStorage {
    StoredDocument upload(UUID groupId, UUID messageId, byte[] data, String extension, String contentType);
    String signedUrl(String bucket, String objectKey, String fileName);
    void delete(StoredDocument document);
    record StoredDocument(String objectKey, String bucket) {}
}
