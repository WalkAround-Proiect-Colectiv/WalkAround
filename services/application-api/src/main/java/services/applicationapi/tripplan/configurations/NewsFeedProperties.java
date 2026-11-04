package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record NewsFeedProperties(
        String currentsApiKey,
        @DefaultValue("https://api.currentsapi.services") URI currentsApiBaseUrl,
        @DefaultValue("PT8S") Duration currentsApiTimeout,
        @DefaultValue("20") int newsPageSize
) {
    public NewsFeedProperties {
        if (!"https".equalsIgnoreCase(currentsApiBaseUrl.getScheme())
                || currentsApiBaseUrl.getHost() == null
                || currentsApiBaseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Currents API base URL must be an HTTPS host");
        }
        if (currentsApiTimeout.isNegative() || currentsApiTimeout.isZero()) {
            throw new IllegalArgumentException("Currents API timeout must be positive");
        }
        if (newsPageSize < 1 || newsPageSize > 300) {
            throw new IllegalArgumentException("News page size must be between 1 and 300");
        }
    }
}
