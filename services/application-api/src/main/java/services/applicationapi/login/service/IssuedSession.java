package services.applicationapi.login.service;

import services.applicationapi.login.dto.SessionResponse;
import java.time.Instant;

public record IssuedSession(SessionResponse session, String refreshToken, Instant refreshExpiresAt) {
    @Override public String toString() { return "IssuedSession[redacted]"; }
}
