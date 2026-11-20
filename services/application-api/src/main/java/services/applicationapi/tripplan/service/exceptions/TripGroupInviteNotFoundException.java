package services.applicationapi.tripplan.service.exceptions;

public class TripGroupInviteNotFoundException extends RuntimeException {

    public TripGroupInviteNotFoundException() {
        super("Invitation is unavailable");
    }
}
