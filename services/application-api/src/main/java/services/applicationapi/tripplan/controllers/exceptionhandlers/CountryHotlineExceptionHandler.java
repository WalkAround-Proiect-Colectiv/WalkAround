package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.CountryHotlineController;
import services.applicationapi.tripplan.service.exceptions.InvalidCountryHotlineRequestException;

@RestControllerAdvice(assignableTypes = CountryHotlineController.class)
public class CountryHotlineExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CountryHotlineExceptionHandler.class);

    @ExceptionHandler(InvalidCountryHotlineRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidCountryHotlineRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Could not load country hotlines", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Country hotlines could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
