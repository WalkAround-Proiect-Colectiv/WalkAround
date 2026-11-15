package services.applicationapi.tts.controllers.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import services.applicationapi.tts.controllers.TextToSpeechController;
import services.applicationapi.tts.service.exceptions.InvalidTextToSpeechRequestException;
import services.applicationapi.tts.service.exceptions.TextToSpeechUnavailableException;

@RestControllerAdvice(assignableTypes = TextToSpeechController.class)
public class TextToSpeechExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TextToSpeechExceptionHandler.class);

    @ExceptionHandler(InvalidTextToSpeechRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalid(InvalidTextToSpeechRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(TextToSpeechUnavailableException.class)
    ResponseEntity<ProblemDetail> handleUnavailable(TextToSpeechUnavailableException exception) {
        log.warn("Text-to-speech provider is unavailable: {}", exception.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "Audio directions are temporarily unavailable");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
