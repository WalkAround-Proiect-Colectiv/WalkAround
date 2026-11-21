package services.applicationapi.poiimages.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import services.applicationapi.poiimages.model.PoiImage;

public interface PoiImageRepository extends Repository<PoiImage, UUID> {

    /** Photo metadata with the uploader's display fields, for lists and responses. */
    record Summary(
            UUID id,
            String poiId,
            UUID uploaderId,
            String uploaderUsername,
            String uploaderFirstName,
            String caption,
            int width,
            int height,
            String imageKitFilePath,
            Instant createdAt
    ) {
    }

    PoiImage save(PoiImage image);

    void delete(PoiImage image);

    Optional<PoiImage> findById(UUID id);

    @Query("""
            select new services.applicationapi.poiimages.repository.PoiImageRepository$Summary(
                i.id, i.poiId, u.id, u.username, u.firstName, i.caption, i.width, i.height,
                i.imageKitFilePath, i.createdAt)
            from PoiImage i join i.uploadedBy u
            where i.poiId = :poiId
            order by i.createdAt desc, i.id
            """)
    List<Summary> findSummariesByPoiId(String poiId);

    @Query("""
            select new services.applicationapi.poiimages.repository.PoiImageRepository$Summary(
                i.id, i.poiId, u.id, u.username, u.firstName, i.caption, i.width, i.height,
                i.imageKitFilePath, i.createdAt)
            from PoiImage i join i.uploadedBy u
            where i.id = :id
            """)
    Optional<Summary> findSummaryById(UUID id);

    long countByPoiIdAndUploadedBy_Id(String poiId, UUID userId);
}
