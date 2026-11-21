package services.applicationapi.poiimages.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageException;
import services.applicationapi.poiimages.service.exceptions.InvalidPoiImageRequestException;
import services.applicationapi.poiimages.service.exceptions.PoiImageForbiddenException;
import services.applicationapi.poiimages.service.exceptions.PoiImageLimitException;
import services.applicationapi.poiimages.service.exceptions.PoiImageNotFoundException;
import services.applicationapi.poiimages.service.exceptions.PoiImageStorageException;
import services.applicationapi.poiimages.service.exceptions.PoiImagesAuthenticationException;

@RestControllerAdvice(assignableTypes = PoiImagesController.class)
public class PoiImagesExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PoiImagesExceptionHandler.class);

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleMalformed() {
        return problem(HttpStatus.BAD_REQUEST, "A place id and a photo are required.");
    }

    @ExceptionHandler(InvalidPoiImageRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidPoiImageRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidPoiImageException.class)
    ResponseEntity<ProblemDetail> handleInvalidImage(InvalidPoiImageException exception) {
        return problem(exception.isTooLarge() ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST,
                exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadTooLarge() {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "The selected photo is too large.");
    }

    @ExceptionHandler(PoiImagesAuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(PoiImagesAuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(PoiImageForbiddenException.class)
    ResponseEntity<ProblemDetail> handleForbidden(PoiImageForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(PoiImageNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(PoiImageNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(PoiImageLimitException.class)
    ResponseEntity<ProblemDetail> handleLimit(PoiImageLimitException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(PoiImageStorageException.class)
    ResponseEntity<ProblemDetail> handleStorage(PoiImageStorageException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unexpected POI photo failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "The photo could not be processed.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
