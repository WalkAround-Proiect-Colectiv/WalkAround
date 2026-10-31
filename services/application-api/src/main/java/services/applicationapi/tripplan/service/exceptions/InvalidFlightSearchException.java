package services.applicationapi.tripplan.service.exceptions;

public class InvalidFlightSearchException extends RuntimeException {

    public InvalidFlightSearchException(String message) {
        super(message);
    }
}
