package services.applicationapi.login.controllers;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import services.applicationapi.login.dto.*;
import services.applicationapi.login.service.LoginService;

@RestController
@RequestMapping("/api/mobile/sessions")
public class MobileSessionController {
    private final LoginService login;
    public MobileSessionController(LoginService login) { this.login = login; }

    @PostMapping(value = "/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MobileSessionResponse> login(@Valid @RequestBody GoogleLoginRequest body) {
        var issued = login.login(body.idToken(), "MOBILE");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MobileSessionResponse(issued.session(), issued.refreshToken()));
    }

    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<MobileSessionResponse> refresh(@Valid @RequestBody RefreshRequest body) {
        var issued = login.refresh(body.refreshToken(), "MOBILE");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new MobileSessionResponse(issued.session(), issued.refreshToken()));
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest body) {
        login.logout(body.refreshToken(), "MOBILE");
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
