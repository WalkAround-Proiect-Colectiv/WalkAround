package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tripplan.controllers.WeatherForecastController;
import services.applicationapi.tripplan.service.exceptions.InvalidWeatherForecastRequestException;
import services.applicationapi.tripplan.service.exceptions.WeatherForecastUnavailableException;

@RestControllerAdvice(assignableTypes = WeatherForecastController.class)
public class WeatherForecastExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WeatherForecastExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> handleMissingCountryCode() {
        return problem(HttpStatus.BAD_REQUEST, "A country code is required");
    }

    @ExceptionHandler(InvalidWeatherForecastRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidWeatherForecastRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(WeatherForecastUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable() {
        return problem(HttpStatus.BAD_GATEWAY, "Weather is temporarily unavailable");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected destination weather failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Weather could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
