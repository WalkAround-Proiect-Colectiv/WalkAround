package services.applicationapi.tripplan.service.groupchat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.configurations.GroupChatProperties;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import services.applicationapi.tripplan.dto.GroupChatReplyResponse;
import services.applicationapi.tripplan.model.GroupChatDocument;
import services.applicationapi.tripplan.model.GroupChatDocumentMetadata;
import services.applicationapi.tripplan.model.GroupChatMessage;
import services.applicationapi.tripplan.repository.GroupChatDocumentRepository;
import services.applicationapi.tripplan.repository.GroupChatRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupMemberRepository;
import services.applicationapi.tripplan.service.exceptions.GroupChatConflictException;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;
import services.applicationapi.tripplan.service.exceptions.GroupChatDocumentNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GroupChatDocumentServiceImpl implements GroupChatDocumentService {
    private static final Logger log = LoggerFactory.getLogger(GroupChatDocumentServiceImpl.class);
    private final GroupChatRepository messages;
    private final GroupChatDocumentRepository documents;
    private final TripPlanGroupMemberRepository members;
    private final GroupChatDocumentStorage storage;
    private final GroupChatDocumentValidator validator;
    private final GroupChatProperties properties;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public GroupChatDocumentServiceImpl(GroupChatRepository messages, GroupChatDocumentRepository documents,
            TripPlanGroupMemberRepository members, GroupChatDocumentStorage storage, GroupChatDocumentValidator validator,
            GroupChatProperties properties, PlatformTransactionManager manager, ObjectMapper mapper) {
        this.messages = messages; this.documents = documents; this.members = members; this.storage = storage;
        this.validator = validator; this.properties = properties; this.transactions = new TransactionTemplate(manager); this.mapper = mapper;
    }

    public GroupChatMessageResponse send(UUID groupId, UUID userId, UUID messageId, String caption,
                                         MultipartFile document, UUID replyToMessageId) {
        requireMember(groupId, userId);
        if (messageId == null) throw new GroupChatInvalidMessageException("Message ID is required");
        String body = caption == null ? "" : caption.strip();
        if (body.length() > properties.maxMessageLength() || body.codePoints().anyMatch(c ->
            Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) throw new GroupChatInvalidMessageException("Document caption is invalid");
        var validated = validator.validate(document);
        var existing = messages.findById(messageId);
        if (existing.isPresent()) return sameDocument(existing.get(), groupId, userId, body, validated, replyToMessageId);
        requireReplyTarget(groupId, replyToMessageId);
        var uploaded = storage.upload(groupId, messageId, validated.bytes(), validated.extension(), validated.metadata().contentType());
        SendResult result;
        try {
            result = transactions.execute(status -> {
                requireMember(groupId, userId);
                requireReplyTarget(groupId, replyToMessageId);
                var saved = messages.insert(messageId, groupId, userId, body, "DOCUMENT", Instant.now(), replyToMessageId);
                if (documents.findByMessageId(saved.id()).isPresent()) {
                    return new SendResult(sameDocument(saved, groupId, userId, body, validated, replyToMessageId), false);
                }
                checkIdentity(saved, groupId, userId, body, replyToMessageId);
                documents.insert(new GroupChatDocument(messageId, uploaded.objectKey(), uploaded.bucket(), validated.sha256(), validated.metadata()));
                var response = response(messages.findById(messageId).orElseThrow());
                messages.setPayload(messageId, mapper.writeValueAsString(new DocumentEvent("GROUP_CHAT_MESSAGE", response.id(),
                    response.conversationId(), response.sequence(), messages.eventSequence(messageId), response.senderId(), response.senderName(),
                    response.body(), response.createdAt(), "DOCUMENT", response.document(), response.replyTo())));
                return new SendResult(response, true);
            });
            if (result == null) throw new IllegalStateException("Document transaction failed");
        } catch (RuntimeException exception) {
            storage.delete(uploaded);
            throw exception;
        }
        if (!result.usedUpload()) storage.delete(uploaded);
        log.info("Saved group chat document message {} in group {}", messageId, groupId);
        return result.response();
    }

    @Transactional(readOnly = true)
    public DownloadLink downloadLink(UUID groupId, UUID userId, UUID messageId) {
        requireMember(groupId, userId);
        messages.findById(messageId).filter(m -> groupId.equals(m.conversationId()) && "DOCUMENT".equals(m.messageType()))
            .orElseThrow(GroupChatDocumentNotFoundException::new);
        var document = documents.findByMessageId(messageId).orElseThrow(GroupChatDocumentNotFoundException::new);
        return new DownloadLink(storage.signedUrl(document.bucketName(), document.objectKey(), document.metadata().fileName()));
    }

    private GroupChatMessageResponse sameDocument(GroupChatMessage message, UUID groupId, UUID userId, String body,
            GroupChatDocumentValidator.ValidatedDocument validated, UUID replyTo) {
        checkIdentity(message, groupId, userId, body, replyTo);
        var document = documents.findByMessageId(message.id()).orElseThrow(GroupChatConflictException::new);
        if (!document.contentSha256().equals(validated.sha256()) || !document.metadata().equals(validated.metadata())) throw new GroupChatConflictException();
        return response(message);
    }
    private void checkIdentity(GroupChatMessage message, UUID groupId, UUID userId, String body, UUID replyTo) {
        if (!groupId.equals(message.conversationId()) || !userId.equals(message.senderId()) || !"DOCUMENT".equals(message.messageType())
            || !body.equals(message.body()) || !Objects.equals(replyTo, message.replyToMessageId())) throw new GroupChatConflictException();
    }
    private GroupChatMessageResponse response(GroupChatMessage message) {
        var reply = message.replyToMessageId() == null ? null : messages.findById(message.replyToMessageId())
            .filter(m -> m.conversationId().equals(message.conversationId())).map(GroupChatReplyResponse::from).orElse(null);
        return GroupChatMessageResponse.from(message, null, null, reply);
    }
    private void requireMember(UUID groupId, UUID userId) {
        if (!members.existsByGroup_IdAndUser_Id(groupId, userId)) throw new TripGroupNotFoundException();
    }
    private void requireReplyTarget(UUID groupId, UUID messageId) {
        if (messageId != null && messages.findById(messageId).filter(m -> groupId.equals(m.conversationId())).isEmpty())
            throw new GroupChatInvalidMessageException("Reply target is not available in this group");
    }
    private record SendResult(GroupChatMessageResponse response, boolean usedUpload) {}
    private record DocumentEvent(String type, UUID messageId, UUID conversationId, long sequence, long eventSequence,
        UUID senderId, String senderName, String body, Instant createdAt, String messageType,
        GroupChatDocumentMetadata document, GroupChatReplyResponse replyTo) {}
}
