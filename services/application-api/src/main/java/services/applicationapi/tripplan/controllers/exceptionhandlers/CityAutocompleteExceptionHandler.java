package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.CityAutocompleteController;
import services.applicationapi.tripplan.service.exceptions.CitySearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidCityQueryException;

@RestControllerAdvice(assignableTypes = CityAutocompleteController.class)
public class CityAutocompleteExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CityAutocompleteExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingCity() {
        return problem(HttpStatus.BAD_REQUEST, "City is required");
    }

    @ExceptionHandler(InvalidCityQueryException.class)
    ResponseEntity<ProblemDetail> handleInvalidCity(InvalidCityQueryException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(CitySearchUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable(CitySearchUnavailableException exception) {
        log.warn("City autocomplete provider is unavailable: {}", exception.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected city autocomplete failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "City suggestions could not be loaded");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
