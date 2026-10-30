package services.applicationapi.login.dto;

import services.applicationapi.register.dto.RegistrationResponse;
import java.time.Instant;

public record SessionResponse(String accessToken, String tokenType, Instant expiresAt,
                              RegistrationResponse user) {}
