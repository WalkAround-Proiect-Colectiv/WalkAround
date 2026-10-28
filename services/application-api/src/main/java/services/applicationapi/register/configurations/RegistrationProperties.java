package services.applicationapi.register.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("app.registration")
public record RegistrationProperties(Google google, List<String> allowedOrigins) {
    public record Google(List<String> clientIds, String jwkSetUri, List<String> issuers) {}
}
