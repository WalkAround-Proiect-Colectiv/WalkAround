package services.applicationapi.tripplan.service.exceptions;

public class TripGroupForbiddenException extends RuntimeException {

    public TripGroupForbiddenException() {
        super("Only a group admin can manage invitations");
    }
}
