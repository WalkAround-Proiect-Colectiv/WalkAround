package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-groups")
public record TripGroupProperties(
        @DefaultValue("2097152") long pictureMaxBytes,
        @DefaultValue("12000000") long pictureMaxPixels,
        @DefaultValue("512") int pictureOutputSize
) {

    public TripGroupProperties {
        if (pictureMaxBytes < 1 || pictureMaxPixels < 1 || pictureOutputSize < 1) {
            throw new IllegalArgumentException("Trip group picture limits must be positive");
        }
    }
}
