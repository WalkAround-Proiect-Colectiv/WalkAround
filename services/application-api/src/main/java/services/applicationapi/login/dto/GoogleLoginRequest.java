package services.applicationapi.login.dto;

import jakarta.validation.constraints.*;

public record GoogleLoginRequest(@NotBlank @Size(max = 16384) String idToken) {}
