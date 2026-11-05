package services.applicationapi.profile.controllers;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.profile.dto.UpdateProfileRequest;
import services.applicationapi.profile.service.ProfileService;
import services.applicationapi.register.dto.RegistrationResponse;

@RestController
@RequestMapping("/api/users/me")
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @PutMapping
    ResponseEntity<RegistrationResponse> update(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody UpdateProfileRequest request) {
        return response(profiles.update(UUID.fromString(jwt.getSubject()), request));
    }

    @PutMapping(value = "/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RegistrationResponse> upload(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        return response(profiles.uploadPicture(UUID.fromString(jwt.getSubject()), file));
    }

    @DeleteMapping("/picture")
    ResponseEntity<RegistrationResponse> remove(@AuthenticationPrincipal Jwt jwt) {
        return response(profiles.removePicture(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping(value = "/picture", produces = MediaType.IMAGE_JPEG_VALUE)
    ResponseEntity<byte[]> picture(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG)
                .body(profiles.picture(UUID.fromString(jwt.getSubject())));
    }

    private ResponseEntity<RegistrationResponse> response(RegistrationResponse profile) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile);
    }
}
