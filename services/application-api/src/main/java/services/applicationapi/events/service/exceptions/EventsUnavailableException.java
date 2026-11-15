package services.applicationapi.events.service.exceptions;

public class EventsUnavailableException extends RuntimeException {

    public EventsUnavailableException() {
        super("Events are temporarily unavailable");
    }

    public EventsUnavailableException(Throwable cause) {
        super("Events are temporarily unavailable", cause);
    }
}
