package services.applicationapi.tripplan.service.exceptions;

public class TripGroupInviteLimitException extends RuntimeException {

    public TripGroupInviteLimitException() {
        super("Too many active invitations. Revoke one before creating another.");
    }
}
