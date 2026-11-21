package services.applicationapi.poiimages.dto;

import java.time.Instant;
import java.util.UUID;
import services.applicationapi.poiimages.repository.PoiImageRepository.Summary;
import services.applicationapi.poiimages.service.PoiImageService.PhotoWithUrls;

/**
 * One photo of a POI. {@code imageUrl} and {@code thumbnailUrl} are signed ImageKit URLs that stop working
 * after {@code POI_IMAGEKIT_SIGNED_URL_TTL} (1 hour by default, rounded up to 10 minutes); list the photos again for fresh ones.
 * {@code mine} tells the app whether to offer deletion; the server still checks ownership.
 */
public record PoiImageResponse(
        UUID id,
        String poiId,
        String caption,
        int width,
        int height,
        String uploadedByName,
        boolean mine,
        Instant createdAt,
        String imageUrl,
        String thumbnailUrl
) {

    public static PoiImageResponse of(PhotoWithUrls photo, UUID currentUserId) {
        Summary summary = photo.summary();
        return new PoiImageResponse(
                summary.id(),
                summary.poiId(),
                summary.caption(),
                summary.width(),
                summary.height(),
                displayName(summary),
                summary.uploaderId().equals(currentUserId),
                summary.createdAt(),
                photo.imageUrl(),
                photo.thumbnailUrl());
    }

    private static String displayName(Summary summary) {
        if (summary.uploaderUsername() != null && !summary.uploaderUsername().isBlank()) {
            return summary.uploaderUsername();
        }
        if (summary.uploaderFirstName() != null && !summary.uploaderFirstName().isBlank()) {
            return summary.uploaderFirstName();
        }
        return "A WalkAround user";
    }
}
