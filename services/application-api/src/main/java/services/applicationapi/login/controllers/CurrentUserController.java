package services.applicationapi.login.controllers;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import services.applicationapi.login.service.LoginService;
import services.applicationapi.register.dto.RegistrationResponse;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class CurrentUserController {
    private final LoginService login;
    public CurrentUserController(LoginService login) { this.login = login; }

    @GetMapping("/me")
    ResponseEntity<RegistrationResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(login.currentUser(UUID.fromString(jwt.getSubject())));
    }
}
