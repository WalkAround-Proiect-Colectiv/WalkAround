package services.applicationapi.tripplan.service.exceptions;

public class FlightSearchUnavailableException extends RuntimeException {

    public FlightSearchUnavailableException(String message) {
        super(message);
    }

    public FlightSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
