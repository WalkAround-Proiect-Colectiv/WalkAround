package services.applicationapi.placesearch.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.placesearch.service.InvalidPlaceSearchException;
import services.applicationapi.placesearch.service.PlaceSearchUnavailableException;

@RestControllerAdvice(assignableTypes = PlaceSearchController.class)
public class PlaceSearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PlaceSearchExceptionHandler.class);

    @ExceptionHandler(InvalidPlaceSearchException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidPlaceSearchException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PlaceSearchUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable(PlaceSearchUnavailableException exception) {
        log.warn("Place search provider is unavailable: {}", exception.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Place search is temporarily unavailable");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
