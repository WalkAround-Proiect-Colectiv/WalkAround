package services.applicationapi.tripplan.model;

import java.util.UUID;

public record GroupChatDocument(UUID messageId, String fileId, String filePath, String contentSha256,
                                GroupChatDocumentMetadata metadata) {}
