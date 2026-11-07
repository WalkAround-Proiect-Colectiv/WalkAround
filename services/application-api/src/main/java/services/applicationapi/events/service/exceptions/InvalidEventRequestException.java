package services.applicationapi.events.service.exceptions;

public class InvalidEventRequestException extends RuntimeException {

    public InvalidEventRequestException(String message) {
        super(message);
    }
}
