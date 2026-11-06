package services.applicationapi.profile.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.profile.configurations.ProfileProperties;
import services.applicationapi.profile.dto.UpdateProfileRequest;
import services.applicationapi.register.dto.RegistrationResponse;
import services.applicationapi.user.model.*;
import services.applicationapi.user.repository.*;

@Service
public class ProfileServiceImpl implements ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileServiceImpl.class);
    private final UserRepository users;
    private final UserProfilePictureRepository pictures;
    private final TransactionTemplate transactions;
    private final ProfileProperties properties;

    public ProfileServiceImpl(UserRepository users, UserProfilePictureRepository pictures,
                              TransactionTemplate transactions, ProfileProperties properties) {
        this.users = users;
        this.pictures = pictures;
        this.transactions = transactions;
        this.properties = properties;
    }

    @Override
    public RegistrationResponse update(UUID userId, UpdateProfileRequest request) {
        try {
            return transactions.execute(status -> {
                User user = lockedUser(userId);
                if (request.username() != null && users.existsByUsernameIgnoreCaseAndIdNot(request.username(), userId)) {
                    throw usernameTaken();
                }
                user.updateProfile(request.username(), request.firstName(), request.lastName(), request.description());
                users.flush();
                log.info("Profile updated: userId={}", userId);
                return RegistrationResponse.from(user);
            });
        } catch (DataIntegrityViolationException exception) {
            // The unique index also protects simultaneous requests for the same username.
            throw usernameTaken();
        }
    }

    @Override
    public RegistrationResponse uploadPicture(UUID userId, MultipartFile file) {
        byte[] image = normalizePicture(file);
        return transactions.execute(status -> {
            User user = lockedUser(userId);
            pictures.save(new UserProfilePicture(userId, image));
            user.updatePicture("/api/users/me/picture?v=" + UUID.randomUUID());
            log.info("Profile picture replaced: userId={}, bytes={}", userId, image.length);
            return RegistrationResponse.from(user);
        });
    }

    @Override
    public RegistrationResponse removePicture(UUID userId) {
        return transactions.execute(status -> {
            User user = lockedUser(userId);
            pictures.deleteById(userId);
            user.updatePicture(null);
            log.info("Profile picture removed: userId={}", userId);
            return RegistrationResponse.from(user);
        });
    }

    @Override
    public byte[] picture(UUID userId) {
        return pictures.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No profile picture found")).getImageData();
    }

    private User lockedUser(UUID id) {
        return users.findForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private ResponseStatusException usernameTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "This username is already taken. Please choose another.");
    }

    private byte[] normalizePicture(MultipartFile file) {
        if (file.isEmpty()) throw invalidPicture();
        if (file.getSize() > properties.pictureMaxBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The selected photo is too large.");
        }
        // Detect the file contents, bound decoded dimensions, and re-encode to strip metadata.
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidPicture();
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("jpeg") && !format.equalsIgnoreCase("png")) throw invalidPicture();
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > properties.pictureMaxPixels()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo dimensions are too large. Please choose a smaller image.");
                }
                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0, (double) properties.pictureOutputSize() / Math.max(width, height));
                BufferedImage output = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, output.getWidth(), output.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(source, 0, 0, output.getWidth(), output.getHeight(), null);
                } finally { graphics.dispose(); }
                var bytes = new ByteArrayOutputStream();
                ImageIO.write(output, "jpeg", bytes);
                return bytes.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException exception) {
            throw invalidPicture();
        }
    }

    private ResponseStatusException invalidPicture() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid JPEG or PNG photo.");
    }
}
