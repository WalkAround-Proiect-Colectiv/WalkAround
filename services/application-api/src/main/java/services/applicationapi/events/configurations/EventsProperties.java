package services.applicationapi.events.configurations;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the upcoming events section.
 *
 * @param provider          {@code serpapi} for live Google Events results or {@code recorded} to replay
 *                          saved responses (no API calls; used for development and tests)
 * @param serpApiKey        the events feature's own SerpApi key; may be blank when {@code provider=recorded}
 * @param cacheTtl          how long a locality's event list is reused before fetching again
 * @param timeZone          zone used to interpret the provider's local event times
 * @param nominatimUserAgent identifies this app to OpenStreetMap Nominatim, as its usage policy requires
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
        @DefaultValue("event-reminders-topic") String reminderTopic
) {
}
