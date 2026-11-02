package services.applicationapi.login.service;

import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.login.configurations.LoginProperties;
import services.applicationapi.login.dto.SessionResponse;
import services.applicationapi.register.dto.RegistrationResponse;
import services.applicationapi.register.service.GoogleIdentityVerifier;
import services.applicationapi.session.model.RefreshSession;
import services.applicationapi.session.repository.RefreshSessionRepository;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class LoginServiceImpl implements LoginService {
    private static final Logger log = LoggerFactory.getLogger(LoginServiceImpl.class);
    private final GoogleIdentityVerifier google;
    private final UserIdentityRepository identities;
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final JwtEncoder encoder;
    private final LoginProperties properties;
    private final TransactionTemplate transactions;
    private final SecureRandom random = new SecureRandom();

    public LoginServiceImpl(GoogleIdentityVerifier google, UserIdentityRepository identities, UserRepository users,
                            RefreshSessionRepository sessions, @Qualifier("applicationJwtEncoder") JwtEncoder encoder,
                            LoginProperties properties, TransactionTemplate transactions) {
        this.google = google;
        this.identities = identities;
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.properties = properties;
        this.transactions = transactions;
    }

    @Override
    public IssuedSession login(String googleIdToken, String clientType) {
        requireSigningKey();
        var identity = google.verify(googleIdToken);
        User user = identities.findByProviderAndProviderSubject("google", identity.subject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account found; please register first"))
                .getUser();
        IssuedSession result = transactions.execute(status -> {
            Instant now = Instant.now();
            UUID id = UUID.randomUUID();
            String token = newRefreshToken(id);
            RefreshSession session = sessions.save(new RefreshSession(id, user, hash(token), clientType,
                    now, now.plus(properties.refreshTokenTtl())));
            return issue(user, session, token, now);
        });
        log.info("Login completed: userId={}", user.getId());
        return result;
    }

    @Override
    public IssuedSession refresh(String token, String clientType) {
        requireSigningKey();
        return transactions.execute(status -> {
            RefreshSession session = sessions.findForUpdate(sessionId(token)).orElseThrow(this::invalidRefresh);
            Instant now = Instant.now();
            if (!valid(session, token, clientType, now)) throw invalidRefresh();
            String replacement = newRefreshToken(session.getId());
            session.rotate(hash(replacement), now);
            IssuedSession result = issue(session.getUser(), session, replacement, now);
            log.debug("Refresh token rotated: sessionId={}", session.getId());
            return result;
        });
    }

    @Override
    public void logout(String token, String clientType) {
        if (token == null || token.isBlank()) return;
        UUID id;
        try { id = sessionId(token); }
        catch (ResponseStatusException ex) { return; }
        transactions.executeWithoutResult(status -> sessions.findForUpdate(id).ifPresent(session -> {
            if (valid(session, token, clientType, Instant.now())) {
                session.revoke(Instant.now());
                log.info("Session revoked: sessionId={}", id);
            }
        }));
    }

    @Override
    public RegistrationResponse currentUser(UUID id) {
        return RegistrationResponse.from(users.findById(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is no longer available")));
    }

    private IssuedSession issue(User user, RefreshSession session, String refreshToken, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getId().toString())
                .audience(List.of(properties.audience())).issuedAt(now).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("token_use", "access").build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedSession(new SessionResponse(accessToken, "Bearer", expiresAt,
                RegistrationResponse.from(user)), refreshToken, session.getExpiresAt());
    }

    private boolean valid(RefreshSession session, String token, String clientType, Instant now) {
        return session.getRevokedAt() == null && session.getExpiresAt().isAfter(now)
                && session.getClientType().equals(clientType)
                && MessageDigest.isEqual(session.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                                          hash(token).getBytes(StandardCharsets.US_ASCII));
    }

    private UUID sessionId(String token) {
        if (token == null || !token.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")) throw invalidRefresh();
        try { return UUID.fromString(token.substring(0, 36)); }
        catch (IllegalArgumentException ex) { throw invalidRefresh(); }
    }

    private String newRefreshToken(UUID id) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return id + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private void requireSigningKey() {
        if (properties.signingSecret().isBlank()) {
            log.warn("Login unavailable: JWT signing key not configured");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Login is not configured");
        }
    }

    private ResponseStatusException invalidRefresh() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired or invalid; please sign in again");
    }
}
