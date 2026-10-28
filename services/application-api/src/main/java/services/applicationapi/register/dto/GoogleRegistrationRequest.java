package services.applicationapi.register.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleRegistrationRequest(@NotBlank @Size(max = 16384) String idToken) {}
