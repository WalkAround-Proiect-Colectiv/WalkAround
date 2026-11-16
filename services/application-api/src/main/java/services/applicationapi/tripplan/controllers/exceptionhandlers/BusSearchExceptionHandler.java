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
import services.applicationapi.tripplan.controllers.BusSearchController;
import services.applicationapi.tripplan.service.exceptions.BusSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidBusSearchException;

@RestControllerAdvice(assignableTypes = BusSearchController.class)
public class BusSearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BusSearchExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidBody() {
        return problem(HttpStatus.BAD_REQUEST, "Valid bus search details are required");
    }

    @ExceptionHandler(InvalidBusSearchException.class)
    ResponseEntity<ProblemDetail> invalidSearch(InvalidBusSearchException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(BusSearchUnavailableException.class)
    ResponseEntity<ProblemDetail> providerFailure() {
        return problem(HttpStatus.BAD_GATEWAY, "Bus search is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unexpected bus search failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Bus search could not be completed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
