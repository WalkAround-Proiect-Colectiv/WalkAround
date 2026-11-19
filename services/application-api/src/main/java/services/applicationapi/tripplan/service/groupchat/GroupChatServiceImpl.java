package services.applicationapi.tripplan.service.groupchat;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.login.configurations.LoginProperties;
import services.applicationapi.tripplan.configurations.GroupChatProperties;
import services.applicationapi.tripplan.dto.GroupChatEventResponse;
import services.applicationapi.tripplan.dto.GroupChatImageResponse;
import services.applicationapi.tripplan.dto.GroupChatMessageRequest;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import services.applicationapi.tripplan.dto.GroupChatPollVoteRequest;
import services.applicationapi.tripplan.dto.GroupChatSocketTicket;
import services.applicationapi.tripplan.model.GroupChatImage;
import services.applicationapi.tripplan.model.GroupChatMessage;
import services.applicationapi.tripplan.repository.GroupChatImageRepository;
import services.applicationapi.tripplan.repository.GroupChatRepository;
import services.applicationapi.tripplan.repository.GroupChatPollRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupMemberRepository;
import services.applicationapi.tripplan.service.exceptions.GroupChatConflictException;
import services.applicationapi.tripplan.service.exceptions.GroupChatForbiddenException;
import services.applicationapi.tripplan.service.exceptions.GroupChatImageNotFoundException;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;
import services.applicationapi.tripplan.service.exceptions.GroupChatPollNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GroupChatServiceImpl implements GroupChatService {

    private static final Logger log = LoggerFactory.getLogger(GroupChatServiceImpl.class);

    private final GroupChatRepository messages;
    private final GroupChatPollRepository polls;
    private final GroupChatImageRepository images;
    private final GroupChatImageStorage imageStorage;
    private final GroupChatImageNormalizer imageNormalizer;
    private final TransactionTemplate transactions;
    private final TripPlanGroupMemberRepository members;
    private final GroupChatProperties properties;
    private final LoginProperties loginProperties;
    private final JwtEncoder jwtEncoder;
    private final ObjectMapper mapper;

    public GroupChatServiceImpl(GroupChatRepository messages, GroupChatPollRepository polls,
                                GroupChatImageRepository images, GroupChatImageStorage imageStorage,
                                GroupChatImageNormalizer imageNormalizer,
                                PlatformTransactionManager transactionManager,
                                TripPlanGroupMemberRepository members,
                                GroupChatProperties properties, LoginProperties loginProperties,
                                @Qualifier("applicationJwtEncoder") JwtEncoder jwtEncoder,
                                ObjectMapper mapper) {
        this.messages = messages;
        this.polls = polls;
        this.images = images;
        this.imageStorage = imageStorage;
        this.imageNormalizer = imageNormalizer;
        this.transactions = new TransactionTemplate(transactionManager);
        this.members = members;
        this.properties = properties;
        this.loginProperties = loginProperties;
        this.jwtEncoder = jwtEncoder;
        this.mapper = mapper;
    }

    @Override
    public GroupChatMessageResponse sendImage(UUID groupId, UUID userId, UUID messageId,
                                              String caption, MultipartFile image) {
        requireMember(groupId, userId);
        if (messageId == null) throw new GroupChatInvalidMessageException("Message ID is required");
        String body = caption == null ? "" : caption.strip();
        if (body.length() > properties.maxMessageLength()
                || body.codePoints().anyMatch(value -> Character.isISOControl(value)
                        && value != '\n' && value != '\r' && value != '\t')) {
            throw new GroupChatInvalidMessageException("Image caption is invalid");
        }
        var normalized = imageNormalizer.normalize(image);
        var existing = messages.findById(messageId);
        if (existing.isPresent()) {
            return sameImage(existing.get(), groupId, userId, body, normalized.sha256());
        }

        var uploaded = imageStorage.upload(groupId, messageId, normalized.jpeg());
        try {
            ImageSendResult result = transactions.execute(status -> {
                requireMember(groupId, userId);
                GroupChatMessage saved = messages.insert(messageId, groupId, userId,
                        body, "IMAGE", Instant.now());
                var previousImage = images.findByMessageId(saved.id());
                if (previousImage.isPresent()) {
                    return new ImageSendResult(sameImage(saved, groupId, userId,
                            body, normalized.sha256()), false);
                }
                if (!saved.conversationId().equals(groupId) || !userId.equals(saved.senderId())
                        || !"IMAGE".equals(saved.messageType()) || !body.equals(saved.body())) {
                    throw new GroupChatConflictException();
                }

                images.insert(new GroupChatImage(saved.id(), uploaded.fileId(), uploaded.filePath(),
                        normalized.sha256(), normalized.width(), normalized.height()));
                GroupChatMessageResponse response = response(saved, userId);
                messages.setPayload(saved.id(), payload(response, messages.eventSequence(saved.id())));
                return new ImageSendResult(response, true);
            });
            if (result == null) throw new IllegalStateException("Image message transaction failed");
            if (!result.usedUpload()) imageStorage.delete(uploaded.fileId());
            log.info("Saved group chat image message {} in conversation {}", messageId, groupId);
            return result.response();
        } catch (RuntimeException exception) {
            imageStorage.delete(uploaded.fileId());
            log.warn("Could not save group chat image message {}: {}", messageId,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GroupChatImageResponse imageUrl(UUID groupId, UUID userId, UUID messageId) {
        requireMember(groupId, userId);
        GroupChatMessage message = messages.findById(messageId)
                .filter(found -> groupId.equals(found.conversationId())
                        && "IMAGE".equals(found.messageType()))
                .orElseThrow(GroupChatImageNotFoundException::new);
        return imageResponse(images.findByMessageId(message.id())
                .orElseThrow(GroupChatImageNotFoundException::new));
    }

    @Override
    @Transactional
    public GroupChatMessageResponse send(UUID groupId, UUID userId, GroupChatMessageRequest request) {
        requireMember(groupId, userId);
        String body = request.body().strip();
        String messageType = request.messageType() == null ? "TEXT" : request.messageType();
        if (!"TEXT".equals(messageType) && !"POLL".equals(messageType)) {
            throw new GroupChatInvalidMessageException("Message type is invalid");
        }
        List<String> options = validatedOptions(messageType, request.options());
        if (body.isBlank() || body.length() > properties.maxMessageLength()
                || body.codePoints().anyMatch(value -> Character.isISOControl(value)
                        && value != '\n' && value != '\r' && value != '\t')) {
            throw new GroupChatInvalidMessageException("Message must contain valid text");
        }

        var existing = messages.findById(request.messageId());
        if (existing.isPresent()) return sameMessage(existing.get(), groupId, userId,
                body, messageType, options);

        try {
            GroupChatMessage saved = messages.insert(request.messageId(), groupId, userId,
                    body, messageType, Instant.now());
            if ("POLL".equals(messageType) && polls.snapshot(saved.id(), userId).isEmpty()) {
                polls.create(saved.id(), options);
            }
            GroupChatMessageResponse response = sameMessage(saved, groupId, userId,
                    body, messageType, options);
            if ("POLL".equals(messageType)) {
                messages.setPayload(saved.id(), payload(response, messages.eventSequence(saved.id())));
            }
            log.info("Saved group chat message {} at sequence {} in conversation {}",
                    response.id(), response.sequence(), groupId);
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new GroupChatConflictException();
        }
    }

    @Override
    @Transactional
    public GroupChatMessageResponse vote(UUID groupId, UUID userId, UUID messageId,
                                         GroupChatPollVoteRequest request) {
        requireMember(groupId, userId);
        GroupChatMessage message = requirePoll(groupId, messageId);
        boolean changed = polls.vote(messageId, userId, request.optionId(), Instant.now());
        GroupChatMessageResponse response = response(message, userId);
        if (changed) publishUpdate(response);
        return response;
    }

    @Override
    @Transactional
    public GroupChatMessageResponse closePoll(UUID groupId, UUID userId, UUID messageId) {
        requireMember(groupId, userId);
        GroupChatMessage message = requirePoll(groupId, messageId);
        if (!userId.equals(message.senderId())) throw new GroupChatForbiddenException();
        boolean changed = polls.close(messageId, Instant.now());
        GroupChatMessageResponse response = response(message, userId);
        if (changed) publishUpdate(response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public long eventCursor(UUID groupId, UUID userId) {
        requireMember(groupId, userId);
        return messages.eventCursor(groupId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupChatEventResponse> events(UUID groupId, UUID userId,
                                               long afterEventSequence, Integer limit) {
        requireMember(groupId, userId);
        if (afterEventSequence < 0) throw new GroupChatInvalidMessageException("Event cursor is invalid");
        int pageSize = limit == null ? properties.pageSize() : limit;
        if (pageSize < 1 || pageSize > properties.pageSize()) {
            throw new GroupChatInvalidMessageException("Event page size is invalid");
        }
        Map<UUID, GroupChatMessageResponse> currentMessages = new HashMap<>();
        return messages.eventsAfter(groupId, afterEventSequence, pageSize).stream()
                .map(event -> new GroupChatEventResponse(event.eventSequence(),
                        currentMessages.computeIfAbsent(event.messageId(), id ->
                                response(messages.findById(id).orElseThrow(), userId))))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupChatMessageResponse> messages(UUID groupId, UUID userId, Long afterSequence,
                                                   Long beforeSequence, Integer limit) {
        requireMember(groupId, userId);
        if (afterSequence != null && beforeSequence != null) {
            throw new GroupChatInvalidMessageException("Choose one message cursor");
        }
        if ((afterSequence != null && afterSequence < 0)
                || (beforeSequence != null && beforeSequence < 1)) {
            throw new GroupChatInvalidMessageException("Message cursor is invalid");
        }
        int pageSize = limit == null ? properties.pageSize() : limit;
        if (pageSize < 1 || pageSize > properties.pageSize()) {
            throw new GroupChatInvalidMessageException("Message page size is invalid");
        }

        List<GroupChatMessage> page = afterSequence != null
                ? messages.after(groupId, afterSequence, pageSize)
                : beforeSequence != null
                ? messages.before(groupId, beforeSequence, pageSize)
                : messages.latest(groupId, pageSize);
        return page.stream().map(message -> response(message, userId)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GroupChatSocketTicket socketTicket(UUID groupId, UUID userId) {
        requireMember(groupId, userId);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.socketTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(loginProperties.issuer())
                .subject(userId.toString())
                .audience(List.of(loginProperties.audience()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("token_use", "group_chat")
                .claim("conversation_id", groupId.toString())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new GroupChatSocketTicket(token, groupId, expiresAt);
    }

    private GroupChatMessageResponse sameMessage(GroupChatMessage message, UUID groupId,
                                                 UUID userId, String body, String messageType,
                                                 List<String> options) {
        if (!groupId.equals(message.conversationId()) || !userId.equals(message.senderId())
                || !body.equals(message.body()) || !messageType.equals(message.messageType())) {
            throw new GroupChatConflictException();
        }
        GroupChatMessageResponse response = response(message, userId);
        if ("POLL".equals(messageType) && !response.poll().options().stream()
                .map(option -> option.label()).toList().equals(options)) {
            throw new GroupChatConflictException();
        }
        return response;
    }

    private List<String> validatedOptions(String messageType, List<String> requested) {
        if ("TEXT".equals(messageType)) {
            if (requested != null && !requested.isEmpty()) {
                throw new GroupChatInvalidMessageException("Text messages cannot have poll options");
            }
            return List.of();
        }
        if (requested == null || requested.size() < 2
                || requested.size() > properties.maxPollOptions()) {
            throw new GroupChatInvalidMessageException("Poll needs 2 to "
                    + properties.maxPollOptions() + " options");
        }
        List<String> labels = requested.stream().map(value -> value == null ? "" : value.strip()).toList();
        var unique = new HashSet<String>();
        for (String label : labels) {
            if (label.isBlank() || label.length() > properties.maxPollOptionLength()
                    || label.codePoints().anyMatch(Character::isISOControl)
                    || !unique.add(label.toLowerCase(Locale.ROOT))) {
                throw new GroupChatInvalidMessageException("Poll options must be distinct, nonblank text of up to "
                        + properties.maxPollOptionLength() + " characters");
            }
        }
        return labels;
    }

    private GroupChatMessage requirePoll(UUID groupId, UUID messageId) {
        GroupChatMessage message = messages.findById(messageId)
                .filter(found -> groupId.equals(found.conversationId()) && "POLL".equals(found.messageType()))
                .orElseThrow(GroupChatPollNotFoundException::new);
        return message;
    }

    private GroupChatMessageResponse response(GroupChatMessage message, UUID viewerId) {
        return GroupChatMessageResponse.from(message, "POLL".equals(message.messageType())
                ? polls.snapshot(message.id(), viewerId).orElseThrow() : null,
                "IMAGE".equals(message.messageType())
                        ? imageResponse(images.findByMessageId(message.id()).orElseThrow()) : null);
    }

    private GroupChatMessageResponse sameImage(GroupChatMessage message, UUID groupId,
                                               UUID userId, String body, String sha256) {
        if (!groupId.equals(message.conversationId()) || !userId.equals(message.senderId())
                || !"IMAGE".equals(message.messageType()) || !body.equals(message.body())
                || !images.findByMessageId(message.id())
                        .map(found -> sha256.equals(found.contentSha256())).orElse(false)) {
            throw new GroupChatConflictException();
        }
        return response(message, userId);
    }

    private GroupChatImageResponse imageResponse(GroupChatImage image) {
        return new GroupChatImageResponse(imageStorage.signedUrl(image.filePath()),
                image.width(), image.height());
    }

    private void publishUpdate(GroupChatMessageResponse response) {
        long eventSequence = messages.nextEventSequence(response.conversationId());
        UUID eventId = UUID.randomUUID();
        messages.enqueueEvent(eventId, response.id(), response.conversationId(), response.sequence(),
                eventSequence, payload(response, eventSequence), Instant.now());
        log.info("Saved poll update event {} for message {} at event sequence {}",
                eventId, response.id(), eventSequence);
    }

    private String payload(GroupChatMessageResponse response, long eventSequence) {
        var poll = response.poll();
        var publicPoll = poll == null ? null : new services.applicationapi.tripplan.dto.GroupChatPollResponse(
                poll.revision(), poll.closedAt(), null, poll.options());
        return mapper.writeValueAsString(new PollChatEvent("GROUP_CHAT_MESSAGE", response.id(),
                response.conversationId(), response.sequence(), eventSequence, response.senderId(),
                response.senderName(), response.body(), response.createdAt(),
                response.messageType(), publicPoll, response.image()));
    }

    private record PollChatEvent(String type, UUID messageId, UUID conversationId,
                                 long sequence, long eventSequence, UUID senderId,
                                 String senderName, String body, Instant createdAt,
                                 String messageType,
                                 services.applicationapi.tripplan.dto.GroupChatPollResponse poll,
                                 GroupChatImageResponse image) {
    }

    private record ImageSendResult(GroupChatMessageResponse response, boolean usedUpload) {
    }

    private void requireMember(UUID groupId, UUID userId) {
        if (!members.existsByGroup_IdAndUser_Id(groupId, userId)) {
            throw new TripGroupNotFoundException();
        }
    }
}
