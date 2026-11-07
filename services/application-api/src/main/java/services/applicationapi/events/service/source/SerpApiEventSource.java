package services.applicationapi.events.service.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.Locality;
import services.applicationapi.events.model.TicketLink;
import services.applicationapi.events.service.exceptions.EventsUnavailableException;

/**
 * Live events from SerpApi's Google Events engine.
 *
 * <p>Each locality costs two searches: {@code "Events in <town>, <county>"} and {@code "Events"} with the
 * town as the search location. Testing showed the two wordings return different events, while paging and
 * date filters only repeated the same results, so no further queries are made.
 */
@Component
@ConditionalOnProperty(name = "app.events.provider", havingValue = "serpapi", matchIfMissing = true)
public class SerpApiEventSource implements EventSource {

    private static final Logger log = LoggerFactory.getLogger(SerpApiEventSource.class);
    private static final String NO_RESULTS_PREFIX = "Google hasn't returned any results";

    private final RestClient restClient;
    private final EventsProperties properties;

    public SerpApiEventSource(
            @Qualifier("eventsSerpApiRestClient") RestClient restClient,
            EventsProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<RawEvent> search(Locality locality) {
        String place = locality.county() == null || locality.county().isBlank()
                ? locality.name()
                : locality.name() + ", " + locality.county();
        List<RawEvent> events = new ArrayList<>(
                query(builder -> builder.queryParam("q", "{query}").build("Events in " + place)));
        String location = serpApiLocation(locality);
        if (location != null) {
            events.addAll(query(builder -> builder.queryParam("q", "Events")
                    .queryParam("location", "{location}")
                    .build(location)));
        }
        return events;
    }

    @Override
    public Optional<RawEventDetails> details(String detailsToken) {
        if (detailsToken == null || detailsToken.isBlank()) {
            return Optional.empty();
        }
        DetailsResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> base(builder, "google_event_details")
                            .queryParam("event_token", "{token}")
                            .build(detailsToken))
                    .retrieve()
                    .body(DetailsResponse.class);
        } catch (RestClientException | EventsUnavailableException exception) {
            log.warn("SerpApi event details request failed: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (response == null || response.eventDetails() == null) {
            return Optional.empty();
        }
        Details details = response.eventDetails();
        return Optional.of(new RawEventDetails(
                details.date() == null ? null : details.date().when(),
                details.address(),
                details.description(),
                tickets(details.ticketInfo())));
    }

    private List<RawEvent> query(Function<UriBuilder, URI> uri) {
        EventsResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> uri.apply(base(builder, "google_events")))
                    .retrieve()
                    .body(EventsResponse.class);
        } catch (RestClientException exception) {
            // The exception message may contain the request URL with the API key, so only log its type.
            log.warn("SerpApi events request failed: {}", exception.getClass().getSimpleName());
            throw new EventsUnavailableException(exception);
        }
        if (response == null) {
            throw new EventsUnavailableException();
        }
        if (response.error() != null) {
            if (response.error().startsWith(NO_RESULTS_PREFIX)) {
                return List.of();
            }
            log.warn("SerpApi events search returned an error");
            throw new EventsUnavailableException();
        }
        if (response.eventsResults() == null) {
            return List.of();
        }
        return response.eventsResults().stream()
                .filter(result -> result.title() != null && !result.title().isBlank())
                .map(SerpApiEventSource::toRawEvent)
                .toList();
    }

    static RawEvent toRawEvent(EventResult result) {
        return new RawEvent(
                result.title().strip(),
                result.date() == null ? null : result.date().startDate(),
                result.date() == null ? null : result.date().when(),
                result.venue() == null ? null : result.venue().name(),
                result.address() == null ? List.of() : result.address(),
                result.thumbnail(),
                tickets(result.ticketInfo()),
                result.eventToken());
    }

    private UriBuilder base(UriBuilder builder, String engine) {
        String key = properties.serpApiKey();
        if (key == null || key.isBlank()) {
            throw new EventsUnavailableException();
        }
        return builder.path("/search.json")
                .queryParam("engine", engine)
                .queryParam("hl", "en")
                .queryParam("gl", "ro")
                .queryParam("api_key", key);
    }

    /** SerpApi's canonical location format, e.g. {@code "Cluj-Napoca,Cluj County,Romania"}. */
    static String serpApiLocation(Locality locality) {
        if (!"RO".equals(locality.countryCode()) || locality.county() == null || locality.county().isBlank()) {
            return null;
        }
        String county = locality.county().endsWith("County") || locality.county().equals("Bucharest")
                ? locality.county()
                : locality.county() + " County";
        return locality.name() + "," + county + ",Romania";
    }

    static List<TicketLink> tickets(List<TicketInfo> ticketInfo) {
        if (ticketInfo == null) {
            return List.of();
        }
        return ticketInfo.stream()
                .filter(ticket -> ticket.link() != null && ticket.link().startsWith("https://"))
                .map(ticket -> new TicketLink(ticket.source(), ticket.link()))
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventsResponse(@JsonProperty("events_results") List<EventResult> eventsResults, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventResult(
            String title,
            EventDate date,
            List<String> address,
            Venue venue,
            String thumbnail,
            @JsonProperty("ticket_info") List<TicketInfo> ticketInfo,
            @JsonProperty("event_token") String eventToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventDate(@JsonProperty("start_date") String startDate, String when) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Venue(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TicketInfo(String source, String link) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetailsResponse(@JsonProperty("event_details") Details eventDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Details(
            EventDate date,
            List<String> address,
            String description,
            @JsonProperty("ticket_info") List<TicketInfo> ticketInfo
    ) {
    }
}
