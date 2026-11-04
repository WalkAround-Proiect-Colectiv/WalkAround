package services.applicationapi.tripplan.service.exceptions;

public class InvalidTripPlanException extends RuntimeException {

    public InvalidTripPlanException(String message) {
        super(message);
    }
}
