package services.applicationapi.tripplan.configurations;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record HistoricalWeatherProperties(
        @DefaultValue("https://archive-api.open-meteo.com") URI openMeteoArchiveBaseUrl,
        @DefaultValue("3") int weatherHistoryYears
) {
    public HistoricalWeatherProperties {
        if (!"https".equalsIgnoreCase(openMeteoArchiveBaseUrl.getScheme())
                || openMeteoArchiveBaseUrl.getHost() == null
                || openMeteoArchiveBaseUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("Open-Meteo archive URL must be an HTTPS host");
        }
        if (weatherHistoryYears < 1 || weatherHistoryYears > 10) {
            throw new IllegalArgumentException("Weather history years must be between 1 and 10");
        }
    }
}
