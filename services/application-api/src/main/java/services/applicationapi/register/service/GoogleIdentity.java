package services.applicationapi.register.service;

public record GoogleIdentity(String subject, String email, String firstName,
                             String lastName, String pictureUrl) {}
