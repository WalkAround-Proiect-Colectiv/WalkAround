package services.applicationapi.tripplan.service;

public class AmbiguousCitySelectionException extends RuntimeException {

    public AmbiguousCitySelectionException(String message) {
        super(message);
    }
}
