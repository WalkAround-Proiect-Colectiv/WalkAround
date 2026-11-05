package services.applicationapi.profile.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.profile.dto.UpdateProfileRequest;
import services.applicationapi.register.dto.RegistrationResponse;

public interface ProfileService {
    RegistrationResponse update(UUID userId, UpdateProfileRequest request);
    RegistrationResponse uploadPicture(UUID userId, MultipartFile file);
    RegistrationResponse removePicture(UUID userId);
    byte[] picture(UUID userId);
}
