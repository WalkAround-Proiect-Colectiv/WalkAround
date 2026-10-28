package services.applicationapi.register.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import services.applicationapi.register.dto.*;
import services.applicationapi.register.service.RegistrationService;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/google")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody GoogleRegistrationRequest request) {
        return registrationService.registerWithGoogle(request.idToken());
    }
}
