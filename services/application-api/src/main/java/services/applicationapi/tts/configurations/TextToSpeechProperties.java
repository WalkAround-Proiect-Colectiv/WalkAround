package services.applicationapi.tts.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.tts")
public record TextToSpeechProperties(
        @DefaultValue("piper") String provider,
        @DefaultValue("http://localhost:5000") URI baseUrl,
        @DefaultValue("PT5S") Duration timeout,
        @DefaultValue("200") int maxTextLength,
        @DefaultValue("500") int cacheMaxEntries
) {

    public TextToSpeechProperties {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("TTS_PROVIDER must be configured");
        }
        String scheme = baseUrl == null ? null : baseUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("TTS base URL must use HTTP or HTTPS");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("TTS timeout must be positive");
        }
        if (maxTextLength < 1 || maxTextLength > 1000) {
            throw new IllegalArgumentException("TTS max text length must be between 1 and 1000");
        }
        if (cacheMaxEntries < 0 || cacheMaxEntries > 10000) {
            throw new IllegalArgumentException("TTS cache max entries must be between 0 and 10000");
        }
    }
}
