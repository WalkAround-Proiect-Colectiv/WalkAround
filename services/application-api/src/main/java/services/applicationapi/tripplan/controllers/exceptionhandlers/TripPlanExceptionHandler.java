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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import services.applicationapi.tripplan.controllers.TripPlanController;
import services.applicationapi.tripplan.service.exceptions.AmbiguousCitySelectionException;
import services.applicationapi.tripplan.service.exceptions.CitySearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidCitySelectionException;
import services.applicationapi.tripplan.service.exceptions.InvalidFlightSearchException;
import services.applicationapi.tripplan.service.exceptions.InvalidTripPlanException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;
import services.applicationapi.tripplan.service.exceptions.TripPlanNotFoundException;

@RestControllerAdvice(assignableTypes = TripPlanController.class)
public class TripPlanExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TripPlanExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleInvalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Valid trip plan details are required");
    }

    @ExceptionHandler({InvalidTripPlanException.class, InvalidFlightSearchException.class,
            InvalidCitySelectionException.class})
    ResponseEntity<ProblemDetail> handleInvalidPlan(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AmbiguousCitySelectionException.class)
    ResponseEntity<ProblemDetail> handleAmbiguousDepartureCity(AmbiguousCitySelectionException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(TripPlanAuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthenticationFailure(TripPlanAuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(TripPlanNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(TripPlanNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({FlightSearchUnavailableException.class, CitySearchUnavailableException.class})
    ResponseEntity<ProblemDetail> handleCityValidationFailure() {
        return problem(HttpStatus.BAD_GATEWAY, "City validation is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected saved trip plan failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Trip plan could not be processed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
