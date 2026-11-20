package services.applicationapi.tripplan.service.groupchat;

import java.util.UUID;

public interface GroupChatImageStorage {

    UploadedImage upload(UUID groupId, UUID messageId, byte[] jpeg);

    String signedUrl(String filePath);

    void delete(String fileId);

    record UploadedImage(String fileId, String filePath) {
    }
}
