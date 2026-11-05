package services.applicationapi.tripplan.service.exceptions;

public class InvalidCountryHotlineRequestException extends RuntimeException {

    public InvalidCountryHotlineRequestException(String message) {
        super(message);
    }
}
