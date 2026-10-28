package services.applicationapi.register.dto;

import java.util.UUID;
import services.applicationapi.user.model.User;

public record RegistrationResponse(UUID id, String username, String firstName,
                                   String lastName, String profilePictureUrl, String description) {
    public static RegistrationResponse from(User user) {
        return new RegistrationResponse(user.getId(), user.getUsername(), user.getFirstName(),
                user.getLastName(), user.getProfilePictureUrl(), user.getDescription());
    }
}
