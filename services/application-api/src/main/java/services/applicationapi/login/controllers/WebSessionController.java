package services.applicationapi.login.controllers;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.login.configurations.LoginProperties;
import services.applicationapi.login.dto.*;
import services.applicationapi.login.service.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

@RestController
@RequestMapping("/api/sessions")
public class WebSessionController {
    private final LoginService login;
    private final LoginProperties properties;

    public WebSessionController(LoginService login, LoginProperties properties) {
        this.login = login;
        this.properties = properties;
    }

    @PostMapping(value = "/google", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<SessionResponse> login(@Valid @RequestBody GoogleLoginRequest body, HttpServletRequest request) {
        requireTrustedOrigin(request);
        return response(login.login(body.idToken(), "WEB"));
    }

    @PostMapping("/refresh")
    ResponseEntity<SessionResponse> refresh(HttpServletRequest request) {
        requireTrustedOrigin(request);
        return response(login.refresh(refreshCookie(request), "WEB"));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        requireTrustedOrigin(request);
        login.logout(refreshCookie(request), "WEB");
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<SessionResponse> response(IssuedSession issued) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(issued.refreshToken(),
                        Duration.between(Instant.now(), issued.refreshExpiresAt())).toString())
                .body(issued.session());
    }

    private ResponseCookie cookie(String token, Duration maxAge) {
        return ResponseCookie.from(properties.cookieName(), token).httpOnly(true)
                .secure(properties.cookieSecure()).sameSite("Strict").path("/api/sessions")
                .maxAge(maxAge).build();
    }

    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> cookie.getName().equals(properties.cookieName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        // These endpoints use an ambient cookie: enforce Origin independently of CORS.
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !properties.allowedOrigins().contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted request origin");
        }
    }
}
