package services.applicationapi.register.service;

public interface GoogleIdentityVerifier {
    GoogleIdentity verify(String idToken);
}
