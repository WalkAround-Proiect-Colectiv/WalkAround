package services.applicationapi.poiimages.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.poiimages.configurations.PoiImageKitProperties;
import services.applicationapi.poiimages.configurations.PoiImagesProperties;
import services.applicationapi.poiimages.model.StoredPoiImage;
import services.applicationapi.poiimages.service.exceptions.PoiImageStorageException;
import tools.jackson.databind.JsonNode;

/**
 * Stores POI photos in ImageKit. It has its own settings ({@code app.poi-images.imagekit}) and HTTP client and
 * does not use any other feature's code; by default the settings read the project's {@code IMAGEKIT_*} account.
 *
 * <ul>
 *   <li>Files are uploaded as private files to {@code /walkaround/poi-images/<poi folder>/<photo id>.jpg},
 *       authenticated with the private key, which never leaves the backend.</li>
 *   <li>Private files can only be viewed through signed URLs: the backend signs each URL with the private key
 *       (HMAC-SHA1 over the path and an expiry time), so a URL stops working after
 *       {@code POI_IMAGEKIT_SIGNED_URL_TTL}.</li>
 *   <li>Thumbnails are not stored; they are made by ImageKit on request with a {@code tr:w-<size>,c-at_max}
 *       transformation (resize down only), which is part of the signed path.</li>
 * </ul>
 * See https://imagekit.io/docs/media-delivery-basic-security.
 */
@Component
public class ImageKitPoiImageStorage implements PoiImageStorage {

    static final String ROOT_FOLDER = "/walkaround/poi-images/";
    private static final Logger log = LoggerFactory.getLogger(ImageKitPoiImageStorage.class);
    private static final String FILE_ID = "[A-Za-z0-9_-]{1,200}";
    private static final long EXPIRY_STEP_SECONDS = 600;

    private final RestClient client;
    private final PoiImageKitProperties imageKit;
    private final PoiImagesProperties properties;
    private final Clock clock;

    @Autowired
    public ImageKitPoiImageStorage(
            @Qualifier("poiImageKitRestClient") RestClient client,
            PoiImageKitProperties imageKit,
            PoiImagesProperties properties
    ) {
        this(client, imageKit, properties, Clock.systemUTC());
    }

    ImageKitPoiImageStorage(RestClient client, PoiImageKitProperties imageKit, PoiImagesProperties properties, Clock clock) {
        this.client = client;
        this.imageKit = imageKit;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public StoredPoiImage upload(UUID imageId, String poiId, byte[] jpeg) {
        requireConfigured();
        String folder = ROOT_FOLDER + folderName(poiId);
        String fileName = imageId + ".jpg";
        String expectedPath = folder + "/" + fileName;

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_JPEG);
        ByteArrayResource file = new ByteArrayResource(jpeg) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new HttpEntity<>(file, fileHeaders));
        parts.add("fileName", fileName);
        parts.add("folder", folder);
        parts.add("useUniqueFileName", "false");
        parts.add("overwriteFile", "false");
        parts.add("isPrivateFile", "true");

        try {
            JsonNode response = client.post()
                    .uri(imageKit.uploadUrl())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(JsonNode.class);
            String fileId = response == null ? "" : response.path("fileId").asString("");
            String filePath = response == null ? "" : response.path("filePath").asString("");
            String fileType = response == null ? "" : response.path("fileType").asString("");
            if (!fileId.matches(FILE_ID) || !expectedPath.equals(filePath) || !"image".equals(fileType)) {
                if (fileId.matches(FILE_ID)) {
                    delete(fileId);
                }
                log.warn("ImageKit returned incomplete metadata for POI photo {}", imageId);
                throw PoiImageStorageException.unavailable(null);
            }
            return new StoredPoiImage(fileId, expectedPath);
        } catch (RestClientResponseException exception) {
            String requestId = exception.getResponseHeaders() == null ? null
                    : exception.getResponseHeaders().getFirst("x-ik-requestId");
            log.warn("ImageKit upload returned HTTP {} for POI photo {} (request ID {})",
                    exception.getStatusCode().value(), imageId, requestId);
            throw PoiImageStorageException.unavailable(exception);
        } catch (RestClientException exception) {
            log.warn("ImageKit upload failed for POI photo {}: {}", imageId, exception.getClass().getSimpleName());
            throw PoiImageStorageException.unavailable(exception);
        }
    }

    @Override
    public String imageUrl(String filePath) {
        return signedUrl(requirePoiPath(filePath).substring(1));
    }

    @Override
    public String thumbnailUrl(String filePath) {
        // c-at_max: never enlarge photos that are already narrower than the thumbnail width.
        return signedUrl("tr:w-" + properties.thumbnailSize() + ",c-at_max/" + requirePoiPath(filePath).substring(1));
    }

    @Override
    public void delete(String fileId) {
        if (imageKit.privateKey().isBlank() || fileId == null || !fileId.matches(FILE_ID)) {
            return;
        }
        try {
            client.delete()
                    .uri(imageKit.filesUrl() + "/" + fileId)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Could not remove ImageKit file {}: {}", fileId, exception.getClass().getSimpleName());
        }
    }

    /**
     * ImageKit folder for a POI. POI ids such as {@code osm:n/123} contain characters that are not allowed or
     * would nest folders, so {@code :} and {@code /} become {@code -}.
     */
    static String folderName(String poiId) {
        return poiId.replace(':', '-').replace('/', '-');
    }

    /** Signs a path relative to the URL endpoint (without a leading slash), including any transformation. */
    private String signedUrl(String relativePath) {
        requireConfigured();
        // The expiry is rounded up to a 10-minute step, so the same photo gets the same URL for a while and
        // browsers can reuse their cached copy instead of downloading it again on every listing.
        long earliest = clock.instant().plus(imageKit.signedUrlTtl()).getEpochSecond();
        String expiry = String.valueOf(Math.ceilDiv(earliest, EXPIRY_STEP_SECONDS) * EXPIRY_STEP_SECONDS);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(imageKit.privateKey().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String signature = HexFormat.of().formatHex(
                    mac.doFinal((relativePath + expiry).getBytes(StandardCharsets.UTF_8)));
            return imageKit.urlEndpoint().replaceAll("/+$", "") + "/" + relativePath
                    + "?ik-t=" + expiry + "&ik-s=" + signature;
        } catch (GeneralSecurityException exception) {
            throw PoiImageStorageException.unavailable(exception);
        }
    }

    private static String requirePoiPath(String filePath) {
        if (filePath == null || !filePath.startsWith(ROOT_FOLDER) || filePath.contains("..")) {
            throw PoiImageStorageException.unavailable(null);
        }
        return filePath;
    }

    private void requireConfigured() {
        if (imageKit.privateKey().isBlank() || imageKit.urlEndpoint().isBlank()) {
            throw PoiImageStorageException.notConfigured();
        }
    }

    private String authorization() {
        String credentials = imageKit.privateKey() + ":";
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
