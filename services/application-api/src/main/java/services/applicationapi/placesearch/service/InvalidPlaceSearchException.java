package services.applicationapi.placesearch.service;

public class InvalidPlaceSearchException extends RuntimeException {

    public InvalidPlaceSearchException(String message) {
        super(message);
    }
}
