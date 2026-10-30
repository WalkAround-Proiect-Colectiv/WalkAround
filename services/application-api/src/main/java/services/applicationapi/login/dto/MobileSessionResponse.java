package services.applicationapi.login.dto;

public record MobileSessionResponse(SessionResponse session, String refreshToken) {}
