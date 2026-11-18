package services.applicationapi.tripplan.service.groupchat;

import java.time.Instant;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.login.configurations.LoginProperties;
import services.applicationapi.tripplan.configurations.GroupChatProperties;
import services.applicationapi.tripplan.dto.GroupChatMessageRequest;
import services.applicationapi.tripplan.dto.GroupChatMessageResponse;
import services.applicationapi.tripplan.dto.GroupChatSocketTicket;
import services.applicationapi.tripplan.model.GroupChatMessage;
import services.applicationapi.tripplan.repository.GroupChatRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupMemberRepository;
import services.applicationapi.tripplan.service.exceptions.GroupChatConflictException;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;

@Service
public class GroupChatServiceImpl implements GroupChatService {

    private static final Logger log = LoggerFactory.getLogger(GroupChatServiceImpl.class);

    private final GroupChatRepository messages;
    private final TripPlanGroupMemberRepository members;
    private final GroupChatProperties properties;
    private final LoginProperties loginProperties;
    private final JwtEncoder jwtEncoder;

    public GroupChatServiceImpl(GroupChatRepository messages, TripPlanGroupMemberRepository members,
                                GroupChatProperties properties, LoginProperties loginProperties,
                                @Qualifier("applicationJwtEncoder") JwtEncoder jwtEncoder) {
        this.messages = messages;
        this.members = members;
        this.properties = properties;
        this.loginProperties = loginProperties;
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    @Transactional
    public GroupChatMessageResponse send(UUID groupId, UUID userId, GroupChatMessageRequest request) {
        requireMember(groupId, userId);
        String body = request.body().strip();
        if (body.isBlank() || body.length() > properties.maxMessageLength()
                || body.codePoints().anyMatch(value -> Character.isISOControl(value)
                        && value != '\n' && value != '\r' && value != '\t')) {
            throw new GroupChatInvalidMessageException("Message must contain valid text");
        }

        var existing = messages.findById(request.messageId());
        if (existing.isPresent()) return sameMessage(existing.get(), groupId, userId, body);

        try {
            GroupChatMessage saved = messages.insert(request.messageId(), groupId, userId, body, Instant.now());
            GroupChatMessageResponse response = sameMessage(saved, groupId, userId, body);
            log.info("Saved group chat message {} at sequence {} in conversation {}",
                    response.id(), response.sequence(), groupId);
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new GroupChatConflictException();
        }
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
        return page.stream().map(GroupChatMessageResponse::from).toList();
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
                                                 UUID userId, String body) {
        if (!groupId.equals(message.conversationId()) || !userId.equals(message.senderId())
                || !body.equals(message.body())) {
            throw new GroupChatConflictException();
        }
        return GroupChatMessageResponse.from(message);
    }

    private void requireMember(UUID groupId, UUID userId) {
        if (!members.existsByGroup_IdAndUser_Id(groupId, userId)) {
            throw new TripGroupNotFoundException();
        }
    }
}
