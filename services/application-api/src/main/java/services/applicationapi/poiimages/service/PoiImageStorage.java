package services.applicationapi.poiimages.service;

import java.util.UUID;
import services.applicationapi.poiimages.model.StoredPoiImage;

/** Where POI photo files are kept. The implementation is ImageKit; tests replace it with a fake. */
public interface PoiImageStorage {

    /** Uploads a JPEG as a private file. Throws {@code PoiImageStorageException} when the service fails. */
    StoredPoiImage upload(UUID imageId, String poiId, byte[] jpeg);

    /** A time-limited URL for the full photo. */
    String imageUrl(String filePath);

    /** A time-limited URL for a thumbnail resized by the storage service. */
    String thumbnailUrl(String filePath);

    /** Removes a file; failures are logged, not thrown, since the photo is already gone from the app. */
    void delete(String fileId);
}
