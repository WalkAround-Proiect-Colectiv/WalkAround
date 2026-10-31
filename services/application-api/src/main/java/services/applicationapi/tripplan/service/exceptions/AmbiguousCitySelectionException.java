package services.applicationapi.tripplan.service.exceptions;

public class AmbiguousCitySelectionException extends RuntimeException {

    public AmbiguousCitySelectionException(String message) {
        super(message);
    }
}
