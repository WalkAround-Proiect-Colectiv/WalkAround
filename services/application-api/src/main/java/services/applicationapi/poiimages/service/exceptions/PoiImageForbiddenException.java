package services.applicationapi.poiimages.service.exceptions;

/** Only the uploader may delete a photo. Maps to 403. */
public class PoiImageForbiddenException extends RuntimeException {

    public PoiImageForbiddenException() {
        super("You can only delete photos you added.");
    }
}
