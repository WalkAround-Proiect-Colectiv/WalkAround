package services.applicationapi.tripplan.service.tripgroup;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.configurations.TripGroupProperties;
import services.applicationapi.tripplan.dto.TripGroupResponse;
import services.applicationapi.tripplan.model.TripPlanGroup;
import services.applicationapi.tripplan.model.TripPlanGroupMember;
import services.applicationapi.tripplan.model.TripPlanGroupPicture;
import services.applicationapi.tripplan.repository.TripPlanGroupMemberRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupPictureRepository;
import services.applicationapi.tripplan.repository.TripPlanGroupRepository;
import services.applicationapi.tripplan.service.exceptions.InvalidTripGroupException;
import services.applicationapi.tripplan.service.exceptions.TripGroupForbiddenException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.UserRepository;

@Service
public class TripGroupServiceImpl implements TripGroupService {

    private static final Logger log = LoggerFactory.getLogger(TripGroupServiceImpl.class);

    private final TripPlanGroupRepository groups;
    private final TripPlanGroupMemberRepository members;
    private final TripPlanGroupPictureRepository pictures;
    private final UserRepository users;
    private final TripGroupProperties properties;

    public TripGroupServiceImpl(TripPlanGroupRepository groups, TripPlanGroupMemberRepository members,
                                TripPlanGroupPictureRepository pictures, UserRepository users,
                                TripGroupProperties properties) {
        this.groups = groups;
        this.members = members;
        this.pictures = pictures;
        this.users = users;
        this.properties = properties;
    }

    @Override
    @Transactional
    public TripGroupResponse create(UUID creatorId, String name, String description,
                                    MultipartFile picture) {
        User creator = users.findById(creatorId).orElseThrow(TripPlanAuthenticationException::new);
        String normalizedName = normalize(name, 100, true, "Group name");
        String normalizedDescription = normalize(description, 1000, false, "Description");
        byte[] image = picture == null ? null : normalizePicture(picture);

        TripPlanGroup group = groups.save(new TripPlanGroup(creator, normalizedName, normalizedDescription));
        members.save(new TripPlanGroupMember(group, creator, "ADMIN"));
        if (image != null) pictures.save(new TripPlanGroupPicture(group.getId(), image));

        log.info("Created trip group {} for user {} withPicture={}", group.getId(), creatorId,
                image != null);
        String pictureUrl = image == null ? null : "/api/trip-plan-groups/" + group.getId() + "/picture";
        return new TripGroupResponse(group.getId(), group.getName(), group.getDescription(),
                "ADMIN", pictureUrl, group.getCreatedAt());
    }

    @Override
    @Transactional
    public TripGroupResponse update(UUID groupId, UUID adminId, String name, String description,
                                    MultipartFile picture, boolean removePicture) {
        TripPlanGroup group = groups.findByIdForUpdate(groupId).orElseThrow(TripGroupNotFoundException::new);
        TripPlanGroupMember membership = members.findByGroup_IdAndUser_Id(groupId, adminId)
                .orElseThrow(TripGroupNotFoundException::new);
        if (!"ADMIN".equals(membership.getRole())) throw new TripGroupForbiddenException();
        if (picture != null && removePicture) {
            throw new InvalidTripGroupException("Choose a new photo or remove the current one");
        }

        String normalizedName = normalize(name, 100, true, "Group name");
        String normalizedDescription = normalize(description, 1000, false, "Description");
        byte[] image = picture == null ? null : normalizePicture(picture);

        group.updateDetails(normalizedName, normalizedDescription);
        if (image != null) {
            pictures.findById(groupId).ifPresentOrElse(
                    existing -> existing.replace(image),
                    () -> pictures.save(new TripPlanGroupPicture(groupId, image)));
        } else if (removePicture && pictures.existsById(groupId)) {
            pictures.deleteById(groupId);
        }

        boolean hasPicture = image != null || (!removePicture && pictures.existsById(groupId));
        String pictureUrl = hasPicture ? "/api/trip-plan-groups/" + groupId + "/picture" : null;
        log.info("Updated trip group {} by admin {} withPicture={}", groupId, adminId, hasPicture);
        return new TripGroupResponse(groupId, group.getName(), group.getDescription(),
                membership.getRole(), pictureUrl, group.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public TripGroupResponse get(UUID groupId, UUID memberId) {
        TripPlanGroupMember membership = members.findByGroup_IdAndUser_Id(groupId, memberId)
                .orElseThrow(TripGroupNotFoundException::new);
        TripPlanGroup group = groups.findById(groupId).orElseThrow(TripGroupNotFoundException::new);
        String pictureUrl = pictures.existsById(groupId)
                ? "/api/trip-plan-groups/" + groupId + "/picture" : null;
        return new TripGroupResponse(groupId, group.getName(), group.getDescription(),
                membership.getRole(), pictureUrl, group.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TripGroupResponse> list(UUID memberId) {
        return members.findByUser_IdOrderByJoinedAtDesc(memberId).stream()
                .map(membership -> {
                    TripPlanGroup group = membership.getGroup();
                    String pictureUrl = pictures.existsById(group.getId())
                            ? "/api/trip-plan-groups/" + group.getId() + "/picture" : null;
                    return new TripGroupResponse(group.getId(), group.getName(), group.getDescription(),
                            membership.getRole(), pictureUrl, group.getCreatedAt());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] picture(UUID groupId, UUID memberId) {
        requireMember(groupId, memberId);
        return pictures.findById(groupId)
                .orElseThrow(TripGroupNotFoundException::new)
                .getImageData();
    }

    private void requireMember(UUID groupId, UUID memberId) {
        if (!members.existsByGroup_IdAndUser_Id(groupId, memberId)) {
            throw new TripGroupNotFoundException();
        }
    }

    private String normalize(String value, int maxLength, boolean required, String label) {
        if (value == null) {
            if (required) throw new InvalidTripGroupException(label + " is required");
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            if (required) throw new InvalidTripGroupException(label + " is required");
            return null;
        }
        if (normalized.length() > maxLength || normalized.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        && (required || (codePoint != '\n' && codePoint != '\r' && codePoint != '\t')))) {
            throw new InvalidTripGroupException(label + " contains invalid characters or is too long");
        }
        return normalized;
    }

    private byte[] normalizePicture(MultipartFile picture) {
        if (picture.isEmpty()) throw new InvalidTripGroupException("Choose a JPEG or PNG group photo");
        if (picture.getSize() > properties.pictureMaxBytes()) {
            throw new InvalidTripGroupException("The group photo is too large");
        }

        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(picture.getBytes()))) {
            if (input == null) throw new InvalidTripGroupException("Choose a JPEG or PNG group photo");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidTripGroupException("Choose a JPEG or PNG group photo");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) {
                    throw new InvalidTripGroupException("Choose a JPEG or PNG group photo");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1
                        || (long) width * height > properties.pictureMaxPixels()) {
                    throw new InvalidTripGroupException("Group photo dimensions are too large");
                }

                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0,
                        (double) properties.pictureOutputSize() / Math.max(width, height));
                BufferedImage output = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
                } finally {
                    graphics.dispose();
                }

                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                if (!ImageIO.write(output, "jpeg", bytes)) {
                    throw new InvalidTripGroupException("Choose a JPEG or PNG group photo");
                }
                return bytes.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new InvalidTripGroupException("Choose a JPEG or PNG group photo");
        }
    }
}
