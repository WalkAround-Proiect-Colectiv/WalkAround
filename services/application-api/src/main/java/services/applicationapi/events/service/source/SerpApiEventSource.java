package services.applicationapi.events.service.source;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.EventVenue;
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
    private final RestClient detailsRestClient;
    private final EventsProperties properties;

    @Autowired
    public SerpApiEventSource(
            @Qualifier("eventsSerpApiRestClient") RestClient restClient,
            @Qualifier("eventsSerpApiDetailsRestClient") RestClient detailsRestClient,
            EventsProperties properties
    ) {
        this.restClient = restClient;
        this.detailsRestClient = detailsRestClient;
        this.properties = properties;
    }

    public SerpApiEventSource(
            RestClient restClient,
            EventsProperties properties
    ) {
        this(restClient, restClient, properties);
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
        String key = properties.serpApiKey();
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        DetailsResponse response;
        try {
            response = detailsRestClient.get()
                    .uri(builder -> baseWithKey(builder, "google_event_details", key)
                            .queryParam("event_token", "{token}")
                            .build(detailsToken))
                    .retrieve()
                    .body(DetailsResponse.class);
        } catch (RestClientException exception) {
            // The exception message may contain the request URL with the API key, so only log its type.
            log.warn("SerpApi event details request failed: {}", exception.getClass().getSimpleName());
            throw new EventsUnavailableException(exception);
        }
        if (response == null) {
            throw new EventsUnavailableException();
        }
        if (response.error() != null && !response.error().isBlank()) {
            if (isNoResultsOrExpiredError(response.error())) {
                return Optional.empty();
            }
            log.warn("SerpApi event details returned an error response");
            throw new EventsUnavailableException();
        }
        if (response.eventDetails() == null) {
            return Optional.empty();
        }
        return Optional.of(toRawEventDetails(response.eventDetails()));
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
            log.warn("SerpApi events error: {}", response.error());
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
        String title = SafeUrls.cap(result.title(), SafeUrls.MAX_TITLE_LENGTH);
        String venueName = result.venue() == null ? null : SafeUrls.cap(result.venue().name(), SafeUrls.MAX_VENUE_NAME_LENGTH);
        EventVenue venue = result.venue() == null
                ? null
                : SafeUrls.sanitizeVenue(
                        result.venue().name(),
                        result.venue().rating(),
                        result.venue().reviews(),
                        result.venue().link()
                );
        String imageUrl = SafeUrls.httpsOrNull(result.image()) != null
                ? SafeUrls.httpsOrNull(result.image())
                : SafeUrls.httpsOrNull(result.thumbnail());
        String mapUrl = result.eventLocationMap() == null
                ? null
                : SafeUrls.httpsOrNull(result.eventLocationMap().link());
        List<String> address = sanitizeAddress(result.address());
        return new RawEvent(
                title != null ? title : result.title().strip(),
                result.date() == null ? null : SafeUrls.cap(result.date().startDate(), SafeUrls.MAX_WHEN_LENGTH),
                result.date() == null ? null : SafeUrls.cap(result.date().when(), SafeUrls.MAX_WHEN_LENGTH),
                venueName,
                address,
                imageUrl,
                tickets(result.ticketInfo()),
                result.eventToken(),
                SafeUrls.cleanDescription(result.description()),
                SafeUrls.httpsOrNull(result.link()),
                mapUrl,
                venue
        );
    }

    static RawEventDetails toRawEventDetails(Details details) {
        EventVenue venue = details.venue() == null
                ? null
                : SafeUrls.sanitizeVenue(
                        details.venue().name(),
                        details.venue().rating(),
                        details.venue().reviews(),
                        details.venue().link()
                );
        String imageUrl = SafeUrls.httpsOrNull(details.image()) != null
                ? SafeUrls.httpsOrNull(details.image())
                : SafeUrls.httpsOrNull(details.thumbnail());
        String mapUrl = details.eventLocationMap() == null
                ? null
                : SafeUrls.httpsOrNull(details.eventLocationMap().link());
        return new RawEventDetails(
                SafeUrls.cap(details.title(), SafeUrls.MAX_TITLE_LENGTH),
                details.date() == null ? null : SafeUrls.cap(details.date().when(), SafeUrls.MAX_WHEN_LENGTH),
                sanitizeAddress(details.address()),
                SafeUrls.cleanDescription(details.description()),
                tickets(details.ticketInfo()),
                SafeUrls.httpsOrNull(details.link()),
                mapUrl,
                venue,
                imageUrl
        );
    }

    private UriBuilder base(UriBuilder builder, String engine) {
        String key = properties.serpApiKey();
        if (key == null || key.isBlank()) {
            throw new EventsUnavailableException();
        }
        return baseWithKey(builder, engine, key);
    }

    private static UriBuilder baseWithKey(UriBuilder builder, String engine, String key) {
        return builder.path("/search.json")
                .queryParam("engine", engine)
                .queryParam("hl", "en")
                .queryParam("gl", "ro")
                .queryParam("api_key", key);
    }

    private static boolean isNoResultsOrExpiredError(String error) {
        String lower = error.toLowerCase(Locale.ROOT);
        return error.startsWith(NO_RESULTS_PREFIX)
                || lower.contains("no results")
                || lower.contains("invalid")
                || lower.contains("expired")
                || lower.contains("not found");
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
                .filter(ticket -> ticket != null && SafeUrls.httpsOrNull(ticket.link()) != null)
                .map(ticket -> new TicketLink(
                        SafeUrls.cap(ticket.source(), SafeUrls.MAX_SOURCE_LENGTH),
                        SafeUrls.httpsOrNull(ticket.link()),
                        ticket.linkType()
                ))
                .toList();
    }

    private static List<String> sanitizeAddress(List<String> address) {
        if (address == null || address.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        int totalLength = 0;
        for (String part : address) {
            String trimmed = SafeUrls.cap(part, SafeUrls.MAX_ADDRESS_LENGTH);
            if (trimmed == null) {
                continue;
            }
            int nextLength = totalLength + (cleaned.isEmpty() ? 0 : 2) + trimmed.length();
            if (nextLength > SafeUrls.MAX_ADDRESS_LENGTH) {
                break;
            }
            cleaned.add(trimmed);
            totalLength = nextLength;
        }
        return List.copyOf(cleaned);
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
            String image,
            String description,
            String link,
            @JsonProperty("event_location_map") LocationMap eventLocationMap,
            @JsonProperty("ticket_info") List<TicketInfo> ticketInfo,
            @JsonProperty("event_token") String eventToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EventDate(@JsonProperty("start_date") String startDate, String when) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Venue(String name, Double rating, Integer reviews, String link) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LocationMap(String image, String link) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TicketInfo(String source, String link, @JsonProperty("link_type") String linkType) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DetailsResponse(@JsonProperty("event_details") Details eventDetails, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Details(
            String title,
            EventDate date,
            List<String> address,
            String description,
            String link,
            String thumbnail,
            String image,
            Venue venue,
            @JsonProperty("event_location_map") LocationMap eventLocationMap,
            @JsonProperty("ticket_info") List<TicketInfo> ticketInfo
    ) {
    }
}
