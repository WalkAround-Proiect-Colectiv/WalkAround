package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.SafetyInformationController;
import services.applicationapi.tripplan.service.exceptions.InvalidSafetyInformationException;
import services.applicationapi.tripplan.service.exceptions.SafetyInformationUnavailableException;

@RestControllerAdvice(assignableTypes = SafetyInformationController.class)
public class SafetyInformationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SafetyInformationExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingCountryCode() {
        return problem(HttpStatus.BAD_REQUEST, "A country code is required");
    }

    @ExceptionHandler(InvalidSafetyInformationException.class)
    ResponseEntity<ProblemDetail> handleInvalidSearch(InvalidSafetyInformationException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(SafetyInformationUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure() {
        return problem(HttpStatus.BAD_GATEWAY, "Safety information is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected destination safety failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Safety information could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
