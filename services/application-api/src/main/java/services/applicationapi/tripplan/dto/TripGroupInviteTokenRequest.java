package services.applicationapi.tripplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TripGroupInviteTokenRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token
) {
}
