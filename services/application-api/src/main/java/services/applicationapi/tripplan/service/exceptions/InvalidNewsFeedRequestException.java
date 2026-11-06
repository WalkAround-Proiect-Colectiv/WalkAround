package services.applicationapi.tripplan.service.exceptions;

public class InvalidNewsFeedRequestException extends RuntimeException {

    public InvalidNewsFeedRequestException(String message) {
        super(message);
    }
}
