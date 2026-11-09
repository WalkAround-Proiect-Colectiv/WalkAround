package services.routingservice.routecalculation.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;

@RestControllerAdvice
public class RouteExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RouteExceptionHandler.class);

    /**
     * Business exceptions: a safe route could not be calculated (422 Unprocessable Entity).
     */
    @ExceptionHandler(NoSafeRouteException.class)
    public ProblemDetail handleNoSafeRoute(NoSafeRouteException exception) {
        log.warn("Could not calculate a safe route: {} (Reason: {})",
                exception.getMessage(), exception.getReason());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage()
        );
        problem.setTitle("No Safe Route Available");
        problem.setType(URI.create("https://api.walkaround.com/errors/no-safe-route"));
        problem.setProperty("reason", exception.getReason().name());
        return problem;
    }

    /**
     * Errors from the external Valhalla service that were not intercepted as NoSafeRoute (502 Bad Gateway).
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleDownstreamRoutingError(RestClientResponseException exception) {
        // The logs must say EXACTLY which service failed, so "Valhalla" stays here.
        log.error("Error communicating with the Valhalla engine. HTTP status: {}, Response: {}",
                exception.getStatusCode().value(), exception.getResponseBodyAsString(), exception);

        // The client gets a generic message (502 Bad Gateway)
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The routing service is experiencing temporary problems. Please try again later."
        );
        problem.setTitle("Routing Service Unavailable"); // Abstracted
        return problem;
    }

    /**
     * JSON parsing or DTO validation errors (400 Bad Request).
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ProblemDetail handleBadRequest(Exception exception) {
        log.warn("Invalid request received: {}", exception.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request format is invalid or contains malformed data."
        );
        problem.setTitle("Bad Request");
        return problem;
    }

    /**
     * Catch-all for any other unexpected server error (500 Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(Exception exception) {
        log.error("Unexpected internal error while processing the route", exception);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred. Please try again later."
        );
        problem.setTitle("Internal Server Error");
        return problem;
    }
}