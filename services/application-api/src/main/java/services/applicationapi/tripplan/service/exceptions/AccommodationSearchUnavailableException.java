package services.applicationapi.tripplan.service.exceptions;

public class AccommodationSearchUnavailableException extends RuntimeException {

    public AccommodationSearchUnavailableException(String message) {
        super(message);
    }

    public AccommodationSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
