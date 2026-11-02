package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.AccommodationSearchController;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidAccommodationSearchException;

@RestControllerAdvice(assignableTypes = AccommodationSearchController.class)
public class AccommodationSearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AccommodationSearchExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> handleInvalidBody() {
        return problem(HttpStatus.BAD_REQUEST, "Valid accommodation search details are required");
    }

    @ExceptionHandler(InvalidAccommodationSearchException.class)
    ResponseEntity<ProblemDetail> handleInvalidSearch(InvalidAccommodationSearchException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AccommodationSearchUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure() {
        return problem(HttpStatus.BAD_GATEWAY, "Accommodation search is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected accommodation search failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Accommodation search could not be completed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
