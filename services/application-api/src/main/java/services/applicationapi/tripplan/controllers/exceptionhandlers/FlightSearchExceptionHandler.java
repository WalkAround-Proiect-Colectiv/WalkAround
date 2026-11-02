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
import services.applicationapi.tripplan.controllers.FlightSearchController;
import services.applicationapi.tripplan.service.exceptions.AmbiguousCitySelectionException;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidCitySelectionException;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;

@RestControllerAdvice(assignableTypes = FlightSearchController.class)
public class FlightSearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FlightSearchExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> handleInvalidBody() {
        return problem(HttpStatus.BAD_REQUEST, "Valid flight search details are required");
    }

    @ExceptionHandler({InvalidFlightSearchException.class, InvalidCitySelectionException.class})
    ResponseEntity<ProblemDetail> handleInvalidSearch(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AmbiguousCitySelectionException.class)
    ResponseEntity<ProblemDetail> handleAmbiguousCity(AmbiguousCitySelectionException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(FlightSearchUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProviderFailure(FlightSearchUnavailableException exception) {
        return problem(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected flight search failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Flight search could not be completed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
