package services.applicationapi.tripplan.service.exceptions;

public class TrainSearchUnavailableException extends RuntimeException {

    public TrainSearchUnavailableException(String message) {
        super(message);
    }

    public TrainSearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
