package services.applicationapi.events.service.exceptions;

public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException() {
        super("This event is no longer available");
    }

    public EventNotFoundException(Throwable cause) {
        super("This event is no longer available", cause);
    }
}
