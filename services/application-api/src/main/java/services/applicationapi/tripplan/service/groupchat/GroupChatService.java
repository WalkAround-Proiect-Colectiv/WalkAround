package services.applicationapi.tripplan.service.groupchat;

import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.dto.GroupChatImageResponse;
import services.applicationapi.tripplan.dto.GroupChatMessageRequest;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import services.applicationapi.tripplan.dto.GroupChatEventResponse;
import services.applicationapi.tripplan.dto.GroupChatPollVoteRequest;
import services.applicationapi.tripplan.dto.GroupChatSocketTicket;

public interface GroupChatService {

    GroupChatMessageResponse send(UUID groupId, UUID userId, GroupChatMessageRequest request);

    GroupChatMessageResponse sendImage(UUID groupId, UUID userId, UUID messageId,
                                       String caption, MultipartFile image);

    GroupChatImageResponse imageUrl(UUID groupId, UUID userId, UUID messageId);

    GroupChatMessageResponse vote(UUID groupId, UUID userId, UUID messageId, GroupChatPollVoteRequest request);

    GroupChatMessageResponse closePoll(UUID groupId, UUID userId, UUID messageId);

    long eventCursor(UUID groupId, UUID userId);

    List<GroupChatEventResponse> events(UUID groupId, UUID userId, long afterEventSequence, Integer limit);

    List<GroupChatMessageResponse> messages(UUID groupId, UUID userId, Long afterSequence,
                                            Long beforeSequence, Integer limit);

    GroupChatSocketTicket socketTicket(UUID groupId, UUID userId);
}
