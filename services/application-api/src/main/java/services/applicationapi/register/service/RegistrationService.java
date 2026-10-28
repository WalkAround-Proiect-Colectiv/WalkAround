package services.applicationapi.register.service;

import services.applicationapi.register.dto.RegistrationResponse;

public interface RegistrationService {
    RegistrationResponse registerWithGoogle(String idToken);
}
