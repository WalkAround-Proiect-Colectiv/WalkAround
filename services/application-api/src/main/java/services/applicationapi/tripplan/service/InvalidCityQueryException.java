package services.applicationapi.tripplan.service;

public class InvalidCityQueryException extends RuntimeException {

    public InvalidCityQueryException(String message) {
        super(message);
    }
}
