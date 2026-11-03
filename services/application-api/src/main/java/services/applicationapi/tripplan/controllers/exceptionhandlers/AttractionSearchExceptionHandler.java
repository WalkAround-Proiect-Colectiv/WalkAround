package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.AttractionSearchController;
import services.applicationapi.tripplan.service.exceptions.AttractionSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAttractionSearchException;

@RestControllerAdvice(assignableTypes = AttractionSearchController.class)
public class AttractionSearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AttractionSearchExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingCountryCode() {
        return problem(HttpStatus.BAD_REQUEST, "A country code is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleInvalidPage() {
        return problem(HttpStatus.BAD_REQUEST, "Page must be between 0 and 5");
    }

    @ExceptionHandler(InvalidAttractionSearchException.class)
    ResponseEntity<ProblemDetail> handleInvalidSearch(InvalidAttractionSearchException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AttractionSearchUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure() {
        return problem(HttpStatus.BAD_GATEWAY, "Attraction search is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected attraction search failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Attractions could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
