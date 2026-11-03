package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record AttractionSearchProperties(
        String serpApiKey,
        @DefaultValue("10") int attractionSearchMaxResults
) {

    public AttractionSearchProperties {
        if (attractionSearchMaxResults < 1 || attractionSearchMaxResults > 20) {
            throw new IllegalArgumentException("Attraction search max results must be between 1 and 20");
        }
    }
}
