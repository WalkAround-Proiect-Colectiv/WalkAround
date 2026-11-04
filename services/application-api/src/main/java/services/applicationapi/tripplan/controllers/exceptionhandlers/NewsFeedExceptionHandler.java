package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.NewsFeedController;
import services.applicationapi.tripplan.service.exceptions.InvalidNewsFeedRequestException;
import services.applicationapi.tripplan.service.exceptions.NewsFeedUnavailableException;

@RestControllerAdvice(assignableTypes = NewsFeedController.class)
public class NewsFeedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(NewsFeedExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingCountryCode() {
        return problem(HttpStatus.BAD_REQUEST, "A country code is required");
    }

    @ExceptionHandler(InvalidNewsFeedRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidNewsFeedRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(NewsFeedUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable() {
        return problem(HttpStatus.BAD_GATEWAY, "News is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected destination news failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "News could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
