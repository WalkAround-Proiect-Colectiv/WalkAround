package services.applicationapi.tripplan.service.exceptions;

public class TripPlanNotFoundException extends RuntimeException {

    public TripPlanNotFoundException() {
        super("Trip plan was not found");
    }
}
