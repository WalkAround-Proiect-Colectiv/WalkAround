package services.applicationapi.tripplan.service.exceptions;

public class InvalidTrainSearchException extends RuntimeException {

    public InvalidTrainSearchException(String message) {
        super(message);
    }
}
