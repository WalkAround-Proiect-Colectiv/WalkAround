package services.applicationapi.events.service.exceptions;

public class EventsAuthenticationException extends RuntimeException {

    public EventsAuthenticationException() {
        super("Please sign in again");
    }

    public EventsAuthenticationException(Throwable cause) {
        super("Please sign in again", cause);
    }
}
