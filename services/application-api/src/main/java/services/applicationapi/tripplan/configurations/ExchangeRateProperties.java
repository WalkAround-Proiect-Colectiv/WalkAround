package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record ExchangeRateProperties(
        @DefaultValue("https://api.frankfurter.dev") URI frankfurterBaseUrl,
        @DefaultValue("PT5S") Duration frankfurterTimeout
) {

    public ExchangeRateProperties {
        if (!"https".equalsIgnoreCase(frankfurterBaseUrl.getScheme())) {
            throw new IllegalArgumentException("Frankfurter base URL must use HTTPS");
        }
        if (frankfurterTimeout.isNegative() || frankfurterTimeout.isZero()) {
            throw new IllegalArgumentException("Frankfurter timeout must be positive");
        }
    }
}
