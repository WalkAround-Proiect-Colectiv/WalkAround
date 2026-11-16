package services.applicationapi.tripplan.configurations;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record BiloTrainProperties(
        String biloApiKey,
        @DefaultValue("https://mcp.bilo.ro/api/v1") URI biloBaseUrl,
        @DefaultValue("PT15S") Duration biloTimeout,
        @DefaultValue("5.3494") BigDecimal biloRonPerEur,
        @DefaultValue("50") int biloSearchPageSize
) {

    public BiloTrainProperties {
        if (!"https".equalsIgnoreCase(biloBaseUrl.getScheme())) {
            throw new IllegalArgumentException("Bilo base URL must use HTTPS");
        }
        if (biloTimeout.isNegative() || biloTimeout.isZero()
                || biloRonPerEur.signum() <= 0
                || biloSearchPageSize < 1 || biloSearchPageSize > 50) {
            throw new IllegalArgumentException("Bilo settings are outside their allowed ranges");
        }
    }
}
