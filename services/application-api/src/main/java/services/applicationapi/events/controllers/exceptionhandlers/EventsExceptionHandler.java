package services.applicationapi.events.controllers.exceptionhandlers;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import services.applicationapi.events.controllers.EventsController;
import services.applicationapi.events.service.exceptions.EventNotFoundException;
import services.applicationapi.events.service.exceptions.EventsAuthenticationException;
import services.applicationapi.events.service.exceptions.EventsUnavailableException;
import services.applicationapi.events.service.exceptions.InvalidEventRequestException;

@RestControllerAdvice(assignableTypes = EventsController.class)
public class EventsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EventsExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> handleInvalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Valid event details are required");
    }

    @ExceptionHandler(InvalidEventRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidEvent(InvalidEventRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(EventsAuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(EventsAuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(EventNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(EventNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(EventsUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable(EventsUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected events failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Events could not be loaded");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
