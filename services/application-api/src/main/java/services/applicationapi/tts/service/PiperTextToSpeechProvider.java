package services.applicationapi.tts.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tts.service.exceptions.TextToSpeechUnavailableException;

@Component
@ConditionalOnProperty(name = "app.tts.provider", havingValue = "piper", matchIfMissing = true)
public class PiperTextToSpeechProvider implements TextToSpeechProvider {

    private static final Logger log = LoggerFactory.getLogger(PiperTextToSpeechProvider.class);
    private static final int MIN_WAV_HEADER_BYTES = 44;

    private final RestClient restClient;

    public PiperTextToSpeechProvider(@Qualifier("piperRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public byte[] synthesize(String text) {
        byte[] audio;
        try {
            audio = restClient.post()
                    .uri("/synthesize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            Throwable cause = rootCause(exception);
            log.warn("Piper TTS request failed; root cause: {}", cause.getClass().getSimpleName());
            throw new TextToSpeechUnavailableException("Audio directions are temporarily unavailable", exception);
        }

        if (!isValidWav(audio)) {
            log.warn("Piper TTS returned an empty or invalid WAV payload");
            throw new TextToSpeechUnavailableException("Audio directions returned an invalid response");
        }

        return audio;
    }

    private static boolean isValidWav(byte[] audio) {
        return audio != null
                && audio.length >= MIN_WAV_HEADER_BYTES
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                && audio[8] == 'W' && audio[9] == 'A' && audio[10] == 'V' && audio[11] == 'E';
    }

    private static Throwable rootCause(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
