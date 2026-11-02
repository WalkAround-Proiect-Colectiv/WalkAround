package services.applicationapi.login.service;

import services.applicationapi.register.dto.RegistrationResponse;
import java.util.UUID;

public interface LoginService {
    IssuedSession login(String googleIdToken, String clientType);
    IssuedSession refresh(String refreshToken, String clientType);
    void logout(String refreshToken, String clientType);
    RegistrationResponse currentUser(UUID userId);
}
