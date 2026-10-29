package services.applicationapi.login.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.login")
public record LoginProperties(String signingSecret, String issuer, String audience,
                              Duration accessTokenTtl, Duration refreshTokenTtl,
                              List<String> allowedOrigins, boolean cookieSecure,
                              String cookieName) {
    public LoginProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || refreshTokenTtl == null || refreshTokenTtl.compareTo(accessTokenTtl) <= 0) {
            throw new IllegalArgumentException("Refresh token TTL must exceed a positive access token TTL");
        }
    }
}
