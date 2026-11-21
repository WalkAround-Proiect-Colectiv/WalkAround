package services.applicationapi.poiimages.service.exceptions;

/** A request with an invalid POI id or caption. Maps to 400. */
public class InvalidPoiImageRequestException extends RuntimeException {

    public InvalidPoiImageRequestException(String message) {
        super(message);
    }
}
