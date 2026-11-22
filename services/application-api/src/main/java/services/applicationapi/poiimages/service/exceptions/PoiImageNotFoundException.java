package services.applicationapi.poiimages.service.exceptions;

/** Maps to 404. */
public class PoiImageNotFoundException extends RuntimeException {

    public PoiImageNotFoundException() {
        super("This photo is no longer available.");
    }
}
