package services.applicationapi.poiimages.service.exceptions;

/** The photo storage service (ImageKit) is not configured or did not respond as expected. Maps to 503. */
public class PoiImageStorageException extends RuntimeException {

    private PoiImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public static PoiImageStorageException notConfigured() {
        return new PoiImageStorageException("Photos are not available on this server yet.", null);
    }

    public static PoiImageStorageException unavailable(Throwable cause) {
        return new PoiImageStorageException("Photo storage is temporarily unavailable. Please try again.", cause);
    }
}
