package services.applicationapi.tripplan.service.exceptions;

public class InvalidCitySelectionException extends RuntimeException {

    public InvalidCitySelectionException(String message) {
        super(message);
    }
}
