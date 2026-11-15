package services.applicationapi.tts.service.exceptions;

public class InvalidTextToSpeechRequestException extends RuntimeException {

    public InvalidTextToSpeechRequestException(String message) {
        super(message);
    }
}
