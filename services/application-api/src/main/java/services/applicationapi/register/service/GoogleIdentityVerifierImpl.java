package services.applicationapi.register.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.register.configurations.RegistrationProperties;

@Service
public class GoogleIdentityVerifierImpl implements GoogleIdentityVerifier {
    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityVerifierImpl.class);
    private final JwtDecoder decoder;
    private final RegistrationProperties properties;

    public GoogleIdentityVerifierImpl(@Qualifier("googleIdTokenDecoder") JwtDecoder decoder,
                                      RegistrationProperties properties) {
        this.decoder = decoder;
        this.properties = properties;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (properties.google().clientIds().stream().noneMatch(id -> !id.isBlank())) {
            log.warn("Google registration requested but no client IDs are configured");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google registration is not configured");
        }
        try {
            Jwt token = decoder.decode(idToken);
            return new GoogleIdentity(token.getSubject(), token.getClaimAsString("email"),
                    profileText(token, "given_name"), profileText(token, "family_name"),
                    token.getClaimAsString("picture"));
        } catch (JwtException | IllegalArgumentException ex) {
            // Never log credentials, claims, or provider error messages.
            log.info("Google registration rejected: invalid identity token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google identity token");
        }
    }

    private String profileText(Jwt token, String claim) {
        String value = token.getClaimAsString(claim);
        return value == null ? null : value.substring(0, Math.min(value.length(), 255));
    }
}
