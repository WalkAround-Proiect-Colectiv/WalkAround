package services.applicationapi.tripplan.service.exceptions;

public class NewsFeedUnavailableException extends RuntimeException {

    public NewsFeedUnavailableException(String message) {
        super(message);
    }

    public NewsFeedUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
