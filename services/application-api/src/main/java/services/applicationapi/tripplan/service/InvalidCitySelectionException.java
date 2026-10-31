package services.applicationapi.tripplan.service;

public class InvalidCitySelectionException extends RuntimeException {

    public InvalidCitySelectionException(String message) {
        super(message);
    }
}
