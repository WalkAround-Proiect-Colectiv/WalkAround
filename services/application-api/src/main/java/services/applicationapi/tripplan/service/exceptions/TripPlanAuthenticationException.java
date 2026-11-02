package services.applicationapi.tripplan.service.exceptions;

public class TripPlanAuthenticationException extends RuntimeException {

    public TripPlanAuthenticationException() {
        super("Valid authentication is required");
    }
}
