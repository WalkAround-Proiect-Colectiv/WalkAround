package services.applicationapi.tripplan.service.exceptions;

public class BusSearchUnavailableException extends RuntimeException {

    public BusSearchUnavailableException(String message) {
        super(message);
    }

    public BusSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
