package services.applicationapi.tripplan.controllers.exceptionhandlers;

import services.applicationapi.tripplan.service.exceptions.GroupChatDocumentNotFoundException;
import services.applicationapi.tripplan.service.exceptions.GroupChatDocumentStorageException;
import services.applicationapi.tripplan.controllers.GroupChatDocumentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import services.applicationapi.tripplan.controllers.GroupChatController;
import services.applicationapi.tripplan.service.exceptions.GroupChatConflictException;
import services.applicationapi.tripplan.service.exceptions.GroupChatForbiddenException;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;
import services.applicationapi.tripplan.service.exceptions.GroupChatImageStorageException;
import services.applicationapi.tripplan.service.exceptions.GroupChatImageNotFoundException;
import services.applicationapi.tripplan.service.exceptions.GroupChatPollNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripGroupNotFoundException;
import services.applicationapi.tripplan.service.exceptions.TripPlanAuthenticationException;

@RestControllerAdvice(assignableTypes = {GroupChatController.class, GroupChatDocumentController.class})
public class GroupChatExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GroupChatExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    ResponseEntity<ProblemDetail> malformed() {
        return problem(HttpStatus.BAD_REQUEST, "Valid chat details are required");
    }

    @ExceptionHandler(GroupChatInvalidMessageException.class)
    ResponseEntity<ProblemDetail> invalid(GroupChatInvalidMessageException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> oversized() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "The attachment is too large");
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ProblemDetail> invalidUpload() {
        return problem(HttpStatus.BAD_REQUEST, "A valid attachment upload is required");
    }

    @ExceptionHandler(GroupChatImageStorageException.class)
    ResponseEntity<ProblemDetail> imageStorage(GroupChatImageStorageException exception) {
        log.warn("Group chat image storage is unavailable: {}", exception.getClass().getSimpleName());
        return problem(HttpStatus.BAD_GATEWAY, "Image service is temporarily unavailable");
    }

    @ExceptionHandler(GroupChatConflictException.class)
    ResponseEntity<ProblemDetail> conflict(GroupChatConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(GroupChatDocumentStorageException.class)
    ResponseEntity<ProblemDetail> documentStorage(GroupChatDocumentStorageException exception) {
        return problem(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(GroupChatDocumentNotFoundException.class)
    ResponseEntity<ProblemDetail> documentMissing(GroupChatDocumentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(GroupChatForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(GroupChatForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(TripGroupNotFoundException.class)
    ResponseEntity<ProblemDetail> inaccessible() {
        return problem(HttpStatus.NOT_FOUND, "Trip group was not found");
    }

    @ExceptionHandler(GroupChatPollNotFoundException.class)
    ResponseEntity<ProblemDetail> pollNotFound(GroupChatPollNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(GroupChatImageNotFoundException.class)
    ResponseEntity<ProblemDetail> imageNotFound(GroupChatImageNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TripPlanAuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated() {
        return problem(HttpStatus.UNAUTHORIZED, "Please sign in again");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unexpected group chat failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Group chat could not be processed");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
