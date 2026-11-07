package services.applicationapi.recommendation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import services.applicationapi.recommendation.exception.InvalidRecommendationRequestException;

@RestControllerAdvice(assignableTypes = ActivityRecommendationController.class)
public class RecommendationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RecommendationExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.isBindingFailure()) {
            return problem(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + fieldError.getField() + "'");
        }
        String detail = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Invalid recommendation request parameters";
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ProblemDetail> handleBindException(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.isBindingFailure()) {
            return problem(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + fieldError.getField() + "'");
        }
        String detail = fieldError != null && fieldError.getDefaultMessage() != null
                ? fieldError.getDefaultMessage()
                : "Invalid recommendation request parameters";
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    ResponseEntity<ProblemDetail> handleMalformedInput() {
        return problem(HttpStatus.BAD_REQUEST, "Invalid recommendation request parameters");
    }

    @ExceptionHandler(InvalidRecommendationRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRecommendation(InvalidRecommendationRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected activity recommendation failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Activity recommendations could not be retrieved");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
