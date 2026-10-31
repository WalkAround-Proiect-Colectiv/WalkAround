package services.applicationapi.tripplan.service.exceptions;

public class InvalidCityQueryException extends RuntimeException {

    public InvalidCityQueryException(String message) {
        super(message);
    }
}
