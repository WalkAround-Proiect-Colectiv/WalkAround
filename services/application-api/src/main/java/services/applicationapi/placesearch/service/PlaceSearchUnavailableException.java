package services.applicationapi.placesearch.service;

public class PlaceSearchUnavailableException extends RuntimeException {

    public PlaceSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public PlaceSearchUnavailableException(String message) {
        super(message);
    }
}
