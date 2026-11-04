package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.trip-plan")
public record AttractionSearchProperties(String serpApiKey) {
}
