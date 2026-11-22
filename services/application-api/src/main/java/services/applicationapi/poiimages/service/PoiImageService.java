package services.applicationapi.poiimages.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import services.applicationapi.poiimages.configurations.PoiImagesProperties;
import services.applicationapi.poiimages.model.PoiImage;
import services.applicationapi.poiimages.model.ProcessedImage;
import services.applicationapi.poiimages.model.StoredPoiImage;
import services.applicationapi.poiimages.repository.PoiImageRepository;
import services.applicationapi.poiimages.repository.PoiImageRepository.Summary;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageRequestException;
import services.applicationapi.poiimages.service.exceptions.PoiImageForbiddenException;
import services.applicationapi.poiimages.service.exceptions.PoiImageLimitException;
import services.applicationapi.poiimages.service.exceptions.PoiImageNotFoundException;
import services.applicationapi.poiimages.service.exceptions.PoiImagesAuthenticationException;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.UserRepository;

/**
 * Lists, adds and deletes the photos attached to POIs. Files go to {@link PoiImageStorage} (ImageKit);
 * the database only records which file belongs to which POI.
 *
 * <p>Like group chat images, an upload happens before the database transaction that records it, so a slow
 * upload does not hold a database connection. If saving the record fails, the uploaded file is deleted again.
 */
@Service
public class PoiImageService {

    /** A photo with time-limited delivery URLs. */
    public record PhotoWithUrls(Summary summary, String imageUrl, String thumbnailUrl) {
    }

    /**
     * POI ids as published in the map tiles: a curated POI's UUID, or an OpenStreetMap reference such as
     * {@code osm:n/123}. Anything else is rejected so arbitrary text cannot be stored as an id.
     */
    private static final Pattern POI_ID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + "|osm:(node|way|relation|[nwr])/-?[0-9]{1,20}");
    private static final int CAPTION_MAX_LENGTH = 200;

    private final PoiImageRepository images;
    private final UserRepository users;
    private final PoiImageProcessor processor;
    private final PoiImageStorage storage;
    private final PoiImagesProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public PoiImageService(
            PoiImageRepository images,
            UserRepository users,
            PoiImageProcessor processor,
            PoiImageStorage storage,
            PoiImagesProperties properties,
            TransactionTemplate transactions
    ) {
        this(images, users, processor, storage, properties, transactions, Clock.systemUTC());
    }

    PoiImageService(
            PoiImageRepository images,
            UserRepository users,
            PoiImageProcessor processor,
            PoiImageStorage storage,
            PoiImagesProperties properties,
            TransactionTemplate transactions,
            Clock clock
    ) {
        this.images = images;
        this.users = users;
        this.processor = processor;
        this.storage = storage;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    public List<PhotoWithUrls> list(String poiId) {
        return images.findSummariesByPoiId(validPoiId(poiId)).stream().map(this::withUrls).toList();
    }

    public PhotoWithUrls add(UUID userId, String poiId, String caption, byte[] upload) {
        String id = validPoiId(poiId);
        String cleanCaption = validCaption(caption);
        if (images.countByPoiIdAndUploadedBy_Id(id, userId) >= properties.maxPerUserPerPoi()) {
            throw new PoiImageLimitException(properties.maxPerUserPerPoi());
        }
        if (users.findById(userId).isEmpty()) {
            throw new PoiImagesAuthenticationException();
        }
        ProcessedImage processed = processor.process(upload);
        UUID imageId = UUID.randomUUID();
        StoredPoiImage stored = storage.upload(imageId, id, processed.jpeg());
        Summary saved;
        try {
            saved = transactions.execute(status -> {
                User user = users.findById(userId).orElseThrow(PoiImagesAuthenticationException::new);
                images.save(new PoiImage(imageId, id, user, cleanCaption, processed.width(), processed.height(),
                        stored, clock.instant()));
                return images.findSummaryById(imageId).orElseThrow(PoiImageNotFoundException::new);
            });
        } catch (RuntimeException exception) {
            // Only an unsaved upload is removed; once the row is committed the file must stay.
            storage.delete(stored.fileId());
            throw exception;
        }
        return withUrls(saved);
    }

    public void delete(UUID userId, UUID imageId) {
        String fileId = transactions.execute(status -> {
            PoiImage image = images.findById(imageId).orElseThrow(PoiImageNotFoundException::new);
            if (!image.getUploadedBy().getId().equals(userId)) {
                throw new PoiImageForbiddenException();
            }
            images.delete(image);
            return image.getImageKitFileId();
        });
        storage.delete(fileId);
    }

    private PhotoWithUrls withUrls(Summary summary) {
        return new PhotoWithUrls(summary, storage.imageUrl(summary.imageKitFilePath()),
                storage.thumbnailUrl(summary.imageKitFilePath()));
    }

    /**
     * Validates a POI id and returns it in the form the map tiles use (lowercase UUID, or {@code osm:n/},
     * {@code osm:w/}, {@code osm:r/}), so the same place can never be stored under two different ids.
     */
    static String validPoiId(String poiId) {
        if (poiId == null || !POI_ID.matcher(poiId).matches()) {
            throw new InvalidPoiImageRequestException("A valid place id is required.");
        }
        if (!poiId.startsWith("osm:")) {
            return poiId.toLowerCase(java.util.Locale.ROOT);
        }
        int slash = poiId.indexOf('/');
        return "osm:" + poiId.charAt(4) + poiId.substring(slash);
    }

    static String validCaption(String caption) {
        if (caption == null || caption.isBlank()) {
            return null;
        }
        String trimmed = caption.strip();
        if (trimmed.length() > CAPTION_MAX_LENGTH) {
            throw new InvalidPoiImageRequestException("Captions can be up to " + CAPTION_MAX_LENGTH + " characters.");
        }
        return trimmed;
    }
}
