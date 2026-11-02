package services.applicationapi.login.configurations;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(LoginProperties.class)
public class LoginConfiguration {
    @Bean("applicationJwtDecoder")
    JwtDecoder applicationJwtDecoder(LoginProperties properties) {
        if (properties.signingSecret().isBlank()) {
            return token -> { throw new BadJwtException("Application token signing is not configured"); };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key(properties))
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                jwt -> jwt.getExpiresAt() != null && jwt.getSubject() != null
                        && jwt.getAudience() != null
                        && jwt.getAudience().contains(properties.audience())
                        && "access".equals(jwt.getClaimAsString("token_use"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Bean("applicationJwtEncoder")
    JwtEncoder applicationJwtEncoder(LoginProperties properties) {
        if (properties.signingSecret().isBlank()) {
            return parameters -> { throw new JwtEncodingException("Application token signing is not configured"); };
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(key(properties)));
    }

    private SecretKey key(LoginProperties properties) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(properties.signingSecret()); }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("JWT_SIGNING_SECRET must be Base64-encoded random bytes");
        }
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SIGNING_SECRET must contain at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
