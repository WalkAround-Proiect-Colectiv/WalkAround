package services.applicationapi.tripplan.service.exceptions;

public class AttractionSearchUnavailableException extends RuntimeException {

    public AttractionSearchUnavailableException(String message) {
        super(message);
    }

    public AttractionSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
