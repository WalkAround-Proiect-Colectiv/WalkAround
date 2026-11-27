package services.applicationapi.tripplan.model;

import java.util.UUID;

public record GroupChatDocument(UUID messageId, String objectKey, String bucketName, String contentSha256,
                                GroupChatDocumentMetadata metadata) {}
