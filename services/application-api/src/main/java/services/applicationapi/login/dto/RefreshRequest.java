package services.applicationapi.login.dto;

import jakarta.validation.constraints.*;

public record RefreshRequest(@NotBlank @Size(max = 256) String refreshToken) {}
