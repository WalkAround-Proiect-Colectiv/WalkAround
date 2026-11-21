package services.applicationapi.poiimages.service.exceptions;

/** The user already attached the maximum number of photos to this POI. Maps to 409. */
public class PoiImageLimitException extends RuntimeException {

    public PoiImageLimitException(int limit) {
        super("You can add up to " + limit + " photos to this place.");
    }
}
