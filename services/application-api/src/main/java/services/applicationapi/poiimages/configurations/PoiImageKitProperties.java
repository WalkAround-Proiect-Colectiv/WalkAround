package services.applicationapi.poiimages.configurations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * ImageKit settings for POI photos, independent of any other feature's configuration. By default they read
 * the shared {@code IMAGEKIT_*} variables (the project's ImageKit account); each can be overridden with a
 * {@code POI_IMAGEKIT_*} variable, for example to use a separate account.
 *
 * @param privateKey    private API key used for uploads, deletes and URL signing; stays on the backend
 * @param urlEndpoint   delivery URL endpoint, e.g. {@code https://ik.imagekit.io/<id>}
 * @param uploadUrl     ImageKit upload API
 * @param filesUrl      ImageKit file management API (used to delete files)
 * @param timeout       timeout for ImageKit API calls
 * @param signedUrlTtl  minimum lifetime of signed photo URLs
 */
@ConfigurationProperties("app.poi-images.imagekit")
public record PoiImageKitProperties(
        @DefaultValue("") String privateKey,
        @DefaultValue("") String urlEndpoint,
        @DefaultValue("https://upload.imagekit.io/api/v1/files/upload") String uploadUrl,
        @DefaultValue("https://api.imagekit.io/v1/files") String filesUrl,
        @DefaultValue("PT10S") Duration timeout,
        @DefaultValue("PT1H") Duration signedUrlTtl
) {
    public PoiImageKitProperties {
        if (!uploadUrl.startsWith("https://") || !filesUrl.startsWith("https://")
                || (!urlEndpoint.isBlank() && !urlEndpoint.startsWith("https://"))
                || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0
                || signedUrlTtl.isNegative() || signedUrlTtl.isZero() || signedUrlTtl.compareTo(Duration.ofDays(1)) > 0) {
            throw new IllegalArgumentException("POI photo ImageKit settings are invalid");
        }
    }
}
