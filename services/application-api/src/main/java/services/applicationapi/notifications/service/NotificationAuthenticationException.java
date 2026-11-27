package services.applicationapi.notifications.service;

public class NotificationAuthenticationException extends RuntimeException {

    public NotificationAuthenticationException() {
        super("Sign in to view notifications");
    }
}
