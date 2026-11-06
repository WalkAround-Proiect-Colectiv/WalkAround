package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record WeatherForecastProperties(
        @DefaultValue("https://api.open-meteo.com") URI openMeteoBaseUrl,
        @DefaultValue("PT8S") Duration openMeteoTimeout,
        @DefaultValue("14") int weatherForecastDays
) {
    public WeatherForecastProperties {
        if (!"https".equalsIgnoreCase(openMeteoBaseUrl.getScheme())
                || openMeteoBaseUrl.getHost() == null
                || openMeteoBaseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Open-Meteo base URL must be an HTTPS host");
        }
        if (openMeteoTimeout.isNegative() || openMeteoTimeout.isZero()) {
            throw new IllegalArgumentException("Open-Meteo timeout must be positive");
        }
        if (weatherForecastDays < 1 || weatherForecastDays > 14) {
            throw new IllegalArgumentException("Weather forecast days must be between 1 and 14");
        }
    }
}
