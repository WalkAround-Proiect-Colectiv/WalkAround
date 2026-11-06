package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.HistoricalWeatherController;
import services.applicationapi.tripplan.service.exceptions.HistoricalWeatherUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidHistoricalWeatherRequestException;

@RestControllerAdvice(assignableTypes = HistoricalWeatherController.class)
public class HistoricalWeatherExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(HistoricalWeatherExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissing(MissingServletRequestParameterException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Missing required parameter: " + exception.getParameterName());
    }

    @ExceptionHandler(InvalidHistoricalWeatherRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidHistoricalWeatherRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(HistoricalWeatherUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable() {
        return problem(HttpStatus.BAD_GATEWAY, "Historical weather is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected historical weather failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Historical weather could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
