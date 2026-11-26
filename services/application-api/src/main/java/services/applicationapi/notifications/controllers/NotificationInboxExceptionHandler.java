package services.applicationapi.notifications.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import services.applicationapi.notifications.service.InvalidNotificationException;
import services.applicationapi.notifications.service.NotificationAuthenticationException;
import services.applicationapi.notifications.service.NotificationNotFoundException;

@RestControllerAdvice(assignableTypes = NotificationInboxController.class)
public class NotificationInboxExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationInboxExceptionHandler.class);

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid notification request");
    }

    @ExceptionHandler(InvalidNotificationException.class)
    ResponseEntity<ProblemDetail> invalidNotification(InvalidNotificationException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NotificationAuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated() {
        return problem(HttpStatus.UNAUTHORIZED, "Sign in to view notifications");
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Notification not found");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Inbox request failed", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Notifications are temporarily unavailable");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
