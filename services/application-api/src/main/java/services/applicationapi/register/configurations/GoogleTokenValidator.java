package services.applicationapi.register.configurations;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.Instant;
import java.util.List;

public class GoogleTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final RegistrationProperties.Google properties;

    public GoogleTokenValidator(RegistrationProperties.Google properties) {
        this.properties = properties;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        String authorizedParty = token.getClaimAsString("azp");
        boolean valid = properties.issuers().contains(token.getClaimAsString("iss"))
                && audiences != null && audiences.stream().anyMatch(properties.clientIds()::contains)
                && (authorizedParty == null || properties.clientIds().contains(authorizedParty))
                && (audiences.size() <= 1 || authorizedParty != null)
                && token.getExpiresAt() != null && token.getExpiresAt().isAfter(Instant.now())
                && token.getIssuedAt() != null && !token.getIssuedAt().isAfter(Instant.now().plusSeconds(60))
                && validText(token.getSubject(), 255)
                && validText(token.getClaimAsString("email"), 320)
                && Boolean.TRUE.equals(token.getClaim("email_verified"));
        return valid ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid Google identity token", null));
    }

    private boolean validText(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }
}
