package services.applicationapi.profile.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties("app.profile")
public record ProfileProperties(@Min(1) long pictureMaxBytes, @Min(1) long pictureMaxPixels,
                                @Min(1) int pictureOutputSize) {}
