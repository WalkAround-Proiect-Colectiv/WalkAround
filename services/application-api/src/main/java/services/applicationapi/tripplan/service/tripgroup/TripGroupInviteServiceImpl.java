package services.applicationapi.tripplan.service.tripgroup;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.tripplan.configurations.TripGroupProperties;
import services.applicationapi.tripplan.dto.TripGroupInvitePreview;
import services.applicationapi.tripplan.dto.TripGroupInviteResponse;
import services.applicationapi.tripplan.dto.TripGroupInviteSummary;
import services.applicationapi.tripplan.dto.TripGroupResponse;
import services.applicationapi.tripplan.model.GroupRole;
import services.applicationapi.tripplan.model.TripPlanGroup;
import services.applicationapi.tripplan.model.TripPlanGroupInvite;
import services.applicationapi.tripplan.model.TripPlanGroupMember;
import services.applicationapi.tripplan.repository.TripPlanGroupInviteRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupMemberRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupPictureRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupRepository;
import services.applicationapi.tripplan.service.exceptions.TripGroupForbiddenException;
import services.applicationapi.tripplan.service.exceptions.TripGroupInviteLimitException;
import services.applicationapi.tripplan.service.exceptions.TripGroupInviteNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.UserRepository;

@Service
public class TripGroupInviteServiceImpl implements TripGroupInviteService {

    private static final Logger log = LoggerFactory.getLogger(TripGroupInviteServiceImpl.class);
    private static final SecureRandom random = new SecureRandom();

    private final TripPlanGroupInviteRepository invites;
    private final TripPlanGroupRepository groups;
    private final TripPlanGroupMemberRepository members;
    private final TripPlanGroupPictureRepository pictures;
    private final UserRepository users;
    private final TripGroupService groupService;
    private final TripGroupProperties properties;

    public TripGroupInviteServiceImpl(TripPlanGroupInviteRepository invites, TripPlanGroupRepository groups,
                                      TripPlanGroupMemberRepository members,
                                      TripPlanGroupPictureRepository pictures, UserRepository users,
                                      TripGroupService groupService, TripGroupProperties properties) {
        this.invites = invites;
        this.groups = groups;
        this.members = members;
        this.pictures = pictures;
        this.users = users;
        this.groupService = groupService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public TripGroupInviteResponse create(UUID groupId, UUID adminId) {
        TripPlanGroup group = groups.findByIdForUpdate(groupId).orElseThrow(TripGroupNotFoundException::new);
        TripPlanGroupMember admin = requireAdmin(groupId, adminId);
        Instant now = Instant.now();
        long active = invites.countByGroup_IdAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(groupId, now);
        if (active >= properties.maxActiveInvites()) throw new TripGroupInviteLimitException();

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TripPlanGroupInvite invite = invites.save(new TripPlanGroupInvite(group, admin.getUser(), hash(token),
                now, now.plus(properties.inviteTtl())));
        log.info("Created invitation {} for trip group {} by admin {}", invite.getId(), groupId, adminId);
        return new TripGroupInviteResponse(invite.getId(), token, invite.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripGroupInviteSummary> list(UUID groupId, UUID adminId) {
        requireAdmin(groupId, adminId);
        return invites.findTop50ByGroup_IdOrderByCreatedAtDesc(groupId).stream()
                .map(invite -> new TripGroupInviteSummary(invite.getId(), invite.getCreatedAt(),
                        invite.getExpiresAt(), invite.getUsedAt(), invite.getRevokedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(UUID groupId, UUID inviteId, UUID adminId) {
        groups.findByIdForUpdate(groupId).orElseThrow(TripGroupNotFoundException::new);
        requireAdmin(groupId, adminId);
        TripPlanGroupInvite invite = invites.findByIdAndGroupIdForUpdate(inviteId, groupId)
                .orElseThrow(TripGroupInviteNotFoundException::new);
        Instant now = Instant.now();
        if (invite.activeAt(now)) {
            invite.revoke(now);
            log.info("Revoked invitation {} for trip group {} by admin {}", inviteId, groupId, adminId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TripGroupInvitePreview preview(String token) {
        TripPlanGroupInvite invite = activeInvite(token);
        UUID groupId = invite.getGroup().getId();
        return new TripGroupInvitePreview(invite.getGroup().getName(), invite.getExpiresAt(),
                pictures.existsById(groupId));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] picture(String token) {
        UUID groupId = activeInvite(token).getGroup().getId();
        return pictures.findById(groupId)
                .orElseThrow(TripGroupInviteNotFoundException::new)
                .getImageData();
    }

    @Override
    @Transactional
    public TripGroupResponse accept(String token, UUID userId) {
        String tokenHash = hash(token);
        UUID groupId = invites.findGroupIdByTokenHash(tokenHash)
                .orElseThrow(TripGroupInviteNotFoundException::new);
        TripPlanGroup group = groups.findByIdForUpdate(groupId).orElseThrow(TripGroupInviteNotFoundException::new);
        TripPlanGroupInvite invite = invites.findByTokenHashForUpdate(tokenHash)
                .filter(found -> found.activeAt(Instant.now()))
                .orElseThrow(TripGroupInviteNotFoundException::new);

        if (members.existsByGroup_IdAndUser_Id(groupId, userId)) {
            return groupService.get(groupId, userId);
        }
        User user = users.findById(userId).orElseThrow(TripPlanAuthenticationException::new);
        members.save(new TripPlanGroupMember(group, user, GroupRole.MEMBER));
        invite.use(user, Instant.now());
        log.info("User {} joined trip group {} through invitation {}", userId, groupId, invite.getId());
        return groupService.get(groupId, userId);
    }

    private TripPlanGroupMember requireAdmin(UUID groupId, UUID userId) {
        TripPlanGroupMember membership = members.findByGroup_IdAndUser_Id(groupId, userId)
                .orElseThrow(TripGroupNotFoundException::new);
        if (!GroupRole.ADMIN.equals(membership.getRole())) throw new TripGroupForbiddenException();
        return membership;
    }

    private TripPlanGroupInvite activeInvite(String token) {
        return invites.findByTokenHash(hash(token))
                .filter(found -> found.activeAt(Instant.now()))
                .orElseThrow(TripGroupInviteNotFoundException::new);
    }

    private String hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new TripGroupInviteNotFoundException();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
