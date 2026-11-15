package services.applicationapi.events.configurations;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the upcoming events section.
 *
 * @param provider           {@code serpapi} for live Google Events results or {@code recorded} to replay
 *                           saved responses (no API calls; used for development and tests)
 * @param serpApiKey         the events feature's own SerpApi key; may be blank when {@code provider=recorded}
 * @param cacheTtl           how long a locality's event list is reused before fetching again
 * @param timeZone           zone used to interpret the provider's local event times
 * @param nominatimUserAgent identifies this app to OpenStreetMap Nominatim, as its usage policy requires
 * @param detailsEnabled     turns on-demand provider enrichment for event details on or off
 * @param detailsCacheTtl    how long successful provider event details are reused
 * @param detailsFailureTtl  negative-cache duration after a failed or empty details lookup
 * @param detailsTimeout     read timeout for on-demand event details calls
 */
@ConfigurationProperties("app.events")
public record EventsProperties(
        @DefaultValue("serpapi") String provider,
        String serpApiKey,
        @DefaultValue("https://serpapi.com") URI serpApiBaseUrl,
        @DefaultValue("PT30S") Duration serpApiTimeout,
        @DefaultValue("PT24H") Duration cacheTtl,
        @DefaultValue("Europe/Bucharest") ZoneId timeZone,
        @DefaultValue("https://nominatim.openstreetmap.org") URI nominatimBaseUrl,
        @DefaultValue("PT5S") Duration nominatimTimeout,
        @DefaultValue("WalkAround-university-project/1.0 (upcoming events)") String nominatimUserAgent,
        @DefaultValue("true") boolean remindersEnabled,
        @DefaultValue("event-reminders-topic") String reminderTopic,
        @DefaultValue("true") boolean detailsEnabled,
        @DefaultValue("P7D") Duration detailsCacheTtl,
        @DefaultValue("PT1H") Duration detailsFailureTtl,
        @DefaultValue("PT8S") Duration detailsTimeout
) {

    @ConstructorBinding
    public EventsProperties {
        detailsCacheTtl = detailsCacheTtl != null ? detailsCacheTtl : Duration.ofDays(7);
        detailsFailureTtl = detailsFailureTtl != null ? detailsFailureTtl : Duration.ofHours(1);
        detailsTimeout = detailsTimeout != null ? detailsTimeout : Duration.ofSeconds(8);
    }

    public EventsProperties(
            String provider,
            String serpApiKey,
            URI serpApiBaseUrl,
            Duration serpApiTimeout,
            Duration cacheTtl,
            ZoneId timeZone,
            URI nominatimBaseUrl,
            Duration nominatimTimeout,
            String nominatimUserAgent,
            boolean remindersEnabled,
            String reminderTopic
    ) {
        this(
                provider,
                serpApiKey,
                serpApiBaseUrl,
                serpApiTimeout,
                cacheTtl,
                timeZone,
                nominatimBaseUrl,
                nominatimTimeout,
                nominatimUserAgent,
                remindersEnabled,
                reminderTopic,
                true,
                Duration.ofDays(7),
                Duration.ofHours(1),
                Duration.ofSeconds(8)
        );
    }
}
