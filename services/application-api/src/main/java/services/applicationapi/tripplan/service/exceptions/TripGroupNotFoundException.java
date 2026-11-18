package services.applicationapi.tripplan.service.exceptions;

public class TripGroupNotFoundException extends RuntimeException {

    public TripGroupNotFoundException() {
        super("Trip group was not found");
    }
}
