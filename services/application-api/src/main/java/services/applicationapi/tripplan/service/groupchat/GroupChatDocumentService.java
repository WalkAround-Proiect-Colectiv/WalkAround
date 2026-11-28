package services.applicationapi.tripplan.service.groupchat;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;

public interface GroupChatDocumentService {
    GroupChatMessageResponse send(UUID groupId, UUID userId, UUID messageId, String caption,
                                  MultipartFile document, UUID replyToMessageId);
    DownloadLink downloadLink(UUID groupId, UUID userId, UUID messageId);
    record DownloadLink(String url) {}
}
