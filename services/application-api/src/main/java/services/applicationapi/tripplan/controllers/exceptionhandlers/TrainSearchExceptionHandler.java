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
import services.applicationapi.tripplan.controllers.TrainSearchController;
import services.applicationapi.tripplan.service.exceptions.InvalidTrainSearchException;
import services.applicationapi.tripplan.service.exceptions.TrainSearchUnavailableException;

@RestControllerAdvice(assignableTypes = TrainSearchController.class)
public class TrainSearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TrainSearchExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> invalidBody() {
        return problem(HttpStatus.BAD_REQUEST, "Valid train search details are required");
    }

    @ExceptionHandler(InvalidTrainSearchException.class)
    ResponseEntity<ProblemDetail> invalidSearch(InvalidTrainSearchException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(TrainSearchUnavailableException.class)
    ResponseEntity<ProblemDetail> providerFailure() {
        return problem(HttpStatus.BAD_GATEWAY, "Train search is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unexpected train search failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Train search could not be completed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
