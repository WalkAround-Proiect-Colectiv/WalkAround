package services.applicationapi.poiimages.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import services.applicationapi.user.model.User;

/**
 * A photo a user attached to a POI. The image file itself is stored in ImageKit; this row only records which
 * ImageKit file belongs to which POI and who added it.
 */
@Entity
@Table(name = "poi_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoiImage {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String poiId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(length = 200)
    private String caption;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(name = "imagekit_file_id", nullable = false, length = 200)
    private String imageKitFileId;

    @Column(name = "imagekit_file_path", nullable = false, length = 500)
    private String imageKitFilePath;

    @Column(nullable = false)
    private Instant createdAt;

    public PoiImage(UUID id, String poiId, User uploadedBy, String caption, int width, int height,
                    StoredPoiImage stored, Instant now) {
        this.id = id;
        this.poiId = poiId;
        this.uploadedBy = uploadedBy;
        this.caption = caption;
        this.width = width;
        this.height = height;
        this.imageKitFileId = stored.fileId();
        this.imageKitFilePath = stored.filePath();
        this.createdAt = now;
    }
}
