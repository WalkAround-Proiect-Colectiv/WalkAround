package services.applicationapi.tts.service.exceptions;

public class TextToSpeechUnavailableException extends RuntimeException {

    public TextToSpeechUnavailableException(String message) {
        super(message);
    }

    public TextToSpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
