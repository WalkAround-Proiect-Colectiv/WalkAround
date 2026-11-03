package services.applicationapi.tripplan.service.exceptions;

public class InvalidSafetyInformationException extends RuntimeException {
    public InvalidSafetyInformationException(String message) {
        super(message);
    }
}
