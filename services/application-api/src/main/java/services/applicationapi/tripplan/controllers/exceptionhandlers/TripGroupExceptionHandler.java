package services.applicationapi.tripplan.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import services.applicationapi.tripplan.controllers.TripGroupController;
import services.applicationapi.tripplan.service.exceptions.InvalidTripGroupException;
import services.applicationapi.tripplan.service.exceptions.TripGroupForbiddenException;
import services.applicationapi.tripplan.service.exceptions.TripGroupInviteLimitException;
import services.applicationapi.tripplan.service.exceptions.TripGroupInviteNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;

@RestControllerAdvice(assignableTypes = TripGroupController.class)
public class TripGroupExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TripGroupExceptionHandler.class);

    @ExceptionHandler(InvalidTripGroupException.class)
    ResponseEntity<ProblemDetail> invalid(InvalidTripGroupException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class,
            MultipartException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> malformedRequest() {
        return problem(HttpStatus.BAD_REQUEST, "Valid group details are required");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> oversizedPicture() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "The group photo is too large");
    }

    @ExceptionHandler(TripPlanAuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated() {
        return problem(HttpStatus.UNAUTHORIZED, "Please sign in again");
    }

    @ExceptionHandler(TripGroupNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound() {
        return problem(HttpStatus.NOT_FOUND, "Trip group was not found");
    }

    @ExceptionHandler(TripGroupInviteNotFoundException.class)
    ResponseEntity<ProblemDetail> invitationUnavailable() {
        return problem(HttpStatus.NOT_FOUND, "Invitation is unavailable");
    }

    @ExceptionHandler(TripGroupForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden() {
        return problem(HttpStatus.FORBIDDEN, "Only a group admin can make this change");
    }

    @ExceptionHandler(TripGroupInviteLimitException.class)
    ResponseEntity<ProblemDetail> inviteLimit(TripGroupInviteLimitException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unexpected trip group failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Trip group could not be processed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
