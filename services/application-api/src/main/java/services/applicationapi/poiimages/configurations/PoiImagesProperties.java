package services.applicationapi.poiimages.configurations;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for POI photos.
 *
 * @param maxBytes          largest accepted upload. The global multipart limit
 *                          ({@code spring.servlet.multipart.max-file-size}) must be at least this large.
 * @param maxPixels         largest accepted decoded image (width x height), to bound memory use
 * @param outputSize        longest side of the stored photo, in pixels
 * @param thumbnailSize     width of thumbnails, which ImageKit resizes on request ({@code tr:w-<size>})
 * @param maxPerUserPerPoi  how many photos one user may attach to one POI
 */
@Validated
@ConfigurationProperties("app.poi-images")
public record PoiImagesProperties(
        @DefaultValue("5242880") @Min(1) long maxBytes,
        @DefaultValue("25000000") @Min(1) long maxPixels,
        @DefaultValue("1600") @Min(1) int outputSize,
        @DefaultValue("320") @Min(1) int thumbnailSize,
        @DefaultValue("10") @Min(1) int maxPerUserPerPoi
) {
}
