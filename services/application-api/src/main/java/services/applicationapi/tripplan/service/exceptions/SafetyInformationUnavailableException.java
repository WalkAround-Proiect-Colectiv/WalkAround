package services.applicationapi.tripplan.service.exceptions;

public class SafetyInformationUnavailableException extends RuntimeException {
    public SafetyInformationUnavailableException(String message) {
        super(message);
    }

    public SafetyInformationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
