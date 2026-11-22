package services.applicationapi.poiimages.service.exceptions;

/** Maps to 401. */
public class PoiImagesAuthenticationException extends RuntimeException {

    public PoiImagesAuthenticationException() {
        super("Please sign in again.");
    }
}
