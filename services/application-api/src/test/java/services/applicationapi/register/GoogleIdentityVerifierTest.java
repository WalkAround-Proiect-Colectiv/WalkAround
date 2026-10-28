package services.applicationapi.register;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.server.ResponseStatusException;
import services.applicationapi.register.configurations.*;
import services.applicationapi.register.service.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GoogleIdentityVerifierTest {
    private static RSAKey key;
    private GoogleIdentityVerifier verifier;

    @BeforeAll
    static void generateKey() throws Exception { key = new RSAKeyGenerator(2048).generate(); }

    @BeforeEach
    void setUp() throws Exception {
        var google = new RegistrationProperties.Google(List.of("our-client"), "unused",
                List.of("https://accounts.google.com", "accounts.google.com"));
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new GoogleTokenValidator(google));
        verifier = new GoogleIdentityVerifierImpl(decoder, new RegistrationProperties(google, List.of()));
    }

    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com")
                .audience("our-client").subject("google-subject")
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("email", "person@example.com").claim("email_verified", true)
                .claim("given_name", "Alex").claim("family_name", "Pop");
    }

    private String sign(JWTClaimsSet claims, RSAKey signingKey) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void acceptsValidSignedIdentity() throws Exception {
        assertThat(verifier.verify(sign(claims().build(), key)))
                .isEqualTo(new GoogleIdentity("google-subject", "person@example.com", "Alex", "Pop", null));
    }

    @Test
    void acceptsAlternateGoogleIssuer() throws Exception {
        assertThat(verifier.verify(sign(claims().issuer("accounts.google.com").build(), key)).subject())
                .isEqualTo("google-subject");
    }

    @Test
    void rejectsInvalidClaims() throws Exception {
        List<JWTClaimsSet> invalid = List.of(
                claims().audience("another-app").build(),
                claims().issuer("https://attacker.example").build(),
                claims().expirationTime(Date.from(Instant.now().minusSeconds(60))).build(),
                claims().expirationTime(null).build(),
                claims().issueTime(null).build(),
                claims().issueTime(Date.from(Instant.now().plusSeconds(300))).build(),
                claims().subject("").build(),
                claims().claim("email_verified", false).build(),
                claims().claim("email", null).build(),
                claims().claim("azp", "another-app").build(),
                claims().audience(List.of("our-client", "another-app")).build());
        for (JWTClaimsSet invalidClaims : invalid) {
            String token = sign(invalidClaims, key);
            assertThatThrownBy(() -> verifier.verify(token))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(401));
        }
    }

    @Test
    void rejectsForgedSignatureAndMalformedToken() throws Exception {
        String forged = sign(claims().build(), new RSAKeyGenerator(2048).generate());
        assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refusesRegistrationWithoutConfiguredAudience() {
        var google = new RegistrationProperties.Google(List.of(), "unused", List.of());
        var disabled = new GoogleIdentityVerifierImpl(token -> { throw new AssertionError("Must not decode"); },
                new RegistrationProperties(google, List.of()));
        assertThatThrownBy(() -> disabled.verify("anything"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(503));
    }
}
