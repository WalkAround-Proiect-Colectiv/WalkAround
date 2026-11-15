package services.applicationapi.tts.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import services.applicationapi.tts.configurations.TextToSpeechProperties;
import services.applicationapi.tts.service.exceptions.InvalidTextToSpeechRequestException;

@Service
public class TextToSpeechServiceImpl implements TextToSpeechService {

    private final TextToSpeechProvider provider;
    private final TextToSpeechProperties properties;
    private final Map<String, byte[]> cache;

    public TextToSpeechServiceImpl(TextToSpeechProvider provider, TextToSpeechProperties properties) {
        this.provider = provider;
        this.properties = properties;
        int maxEntries = properties.cacheMaxEntries();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > maxEntries;
            }
        });
    }

    @Override
    public byte[] synthesize(String text) {
        if (text == null) {
            throw new InvalidTextToSpeechRequestException("Speech text must not be blank");
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new InvalidTextToSpeechRequestException("Speech text must not be blank");
        }
        if (normalized.length() > properties.maxTextLength()) {
            throw new InvalidTextToSpeechRequestException(
                    "Speech text must not exceed " + properties.maxTextLength() + " characters");
        }

        if (properties.cacheMaxEntries() <= 0) {
            return provider.synthesize(normalized);
        }

        String key = sha256Hex(normalized);
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        byte[] audio = provider.synthesize(normalized);
        cache.put(key, audio);
        return audio;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
