package services.applicationapi.tripplan.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.service.AmbiguousCitySelectionException;
import services.applicationapi.tripplan.service.InvalidCitySelectionException;

@RestControllerAdvice(assignableTypes = CityAirportController.class)
public class CityAirportExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CityAirportExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingCountryCode() {
        return problem(HttpStatus.BAD_REQUEST, "Country code is required");
    }

    @ExceptionHandler(InvalidCitySelectionException.class)
    ResponseEntity<ProblemDetail> handleInvalidSelection(InvalidCitySelectionException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(AmbiguousCitySelectionException.class)
    ResponseEntity<ProblemDetail> handleAmbiguousSelection(AmbiguousCitySelectionException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected city airport lookup failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Airports could not be loaded");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
