package services.applicationapi.profile.dto;

import jakarta.validation.constraints.*;

public record UpdateProfileRequest(
        @Pattern(regexp = "[A-Za-z0-9_]{3,50}", message = "Username must be 3–50 letters, numbers or underscores") String username,
        @Size(max = 255, message = "First name must be at most 255 characters") String firstName,
        @Size(max = 255, message = "Last name must be at most 255 characters") String lastName,
        @Size(max = 2000, message = "Description must be at most 2000 characters") String description) {
    public UpdateProfileRequest {
        username = clean(username);
        firstName = clean(firstName);
        lastName = clean(lastName);
        description = clean(description);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
