package services.applicationapi.tts.controllers;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import services.applicationapi.tts.service.TextToSpeechService;

@RestController
@RequestMapping("/api/v1/tts")
public class TextToSpeechController {

    private static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");
    private static final CacheControl CACHE_CONTROL = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

    private final TextToSpeechService service;

    public TextToSpeechController(TextToSpeechService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<byte[]> synthesize(@RequestParam(required = false) String text) {
        byte[] audio = service.synthesize(text);
        return ResponseEntity.ok()
                .contentType(AUDIO_WAV)
                .cacheControl(CACHE_CONTROL)
                .body(audio);
    }
}
