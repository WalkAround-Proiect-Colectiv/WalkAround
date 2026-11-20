package services.applicationapi.tripplan.model;

import java.util.UUID;

public record GroupChatImage(UUID messageId, String fileId, String filePath,
                             String contentSha256, int width, int height) {
}
