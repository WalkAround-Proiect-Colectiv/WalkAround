package services.applicationapi.tripplan.configurations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.group-chat.imagekit")
public record ImageKitProperties(
        @DefaultValue("") String privateKey,
        @DefaultValue("") String urlEndpoint,
        @DefaultValue("https://upload.imagekit.io/api/v1/files/upload") String uploadUrl,
        @DefaultValue("https://api.imagekit.io/v1/files") String filesUrl,
        @DefaultValue("PT10S") Duration timeout,
        @DefaultValue("PT1H") Duration signedUrlTtl
) {
    public ImageKitProperties {
        if (!uploadUrl.startsWith("https://") || !filesUrl.startsWith("https://")
                || (!urlEndpoint.isBlank() && !urlEndpoint.startsWith("https://"))
                || timeout.isNegative() || timeout.isZero()
                || timeout.compareTo(Duration.ofMinutes(1)) > 0
                || signedUrlTtl.isNegative() || signedUrlTtl.isZero()
                || signedUrlTtl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("ImageKit settings are invalid");
        }
    }
}
