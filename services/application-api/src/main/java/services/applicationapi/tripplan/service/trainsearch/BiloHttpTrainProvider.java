package services.applicationapi.tripplan.service.trainsearch;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.tripplan.configurations.BiloTrainProperties;
import services.applicationapi.tripplan.dto.TrainJourneyResponse;
import services.applicationapi.tripplan.dto.TrainLegResponse;
import services.applicationapi.tripplan.service.exceptions.TrainSearchUnavailableException;
import tools.jackson.databind.JsonNode;

@Component
public class BiloHttpTrainProvider implements BiloTrainProvider {

    private static final Logger log = LoggerFactory.getLogger(BiloHttpTrainProvider.class);
    private static final ZoneId BUCHAREST = ZoneId.of("Europe/Bucharest");

    private final RestClient client;
    private final BiloTrainProperties properties;

    public BiloHttpTrainProvider(@Qualifier("biloTrainRestClient") RestClient client,
                                 BiloTrainProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<Offer> search(String origin, String destination, LocalDate date) {
        if (properties.biloApiKey() == null || properties.biloApiKey().isBlank()) {
            return List.of();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", origin);
        body.put("destination", destination);
        body.put("date", date.toString());
        body.put("passengers", 1);
        body.put("mode", "rail");
        body.put("railClass", "2");
        body.put("sort", "price");
        body.put("limit", properties.biloSearchPageSize());

        JsonNode response;
        try {
            response = client.post()
                    .uri("/search")
                    .header("Authorization", "Bearer " + properties.biloApiKey())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            log.warn("Bilo train search returned HTTP {} on {}",
                    exception.getStatusCode().value(), date);
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable", exception);
        } catch (RestClientException exception) {
            log.warn("Bilo train search failed on {}: {}", date, exception.getClass().getSimpleName());
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable", exception);
        }

        if (response == null || !response.path("trips").isArray()) {
            log.warn("Bilo train search returned an invalid response on {}", date);
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable");
        }
        if (response.path("partialCatalog").asBoolean(false)) {
            log.warn("Bilo train search has partial source coverage on {}", date);
        }

        List<Offer> offers = new ArrayList<>();
        boolean partialCatalog = response.path("partialCatalog").asBoolean(false);
        for (JsonNode trip : response.path("trips")) {
            Offer offer = offer(trip, partialCatalog);
            if (offer != null) offers.add(offer);
        }
        if (offers.isEmpty()) {
            log.info("Bilo returned {} journeys but no priced rail offers on {} (partial catalog: {})",
                    response.path("trips").size(), date, partialCatalog);
        }
        log.debug("Bilo returned {} priced rail journeys on {}", offers.size(), date);
        return List.copyOf(offers);
    }

    private Offer offer(JsonNode trip, boolean partialCatalog) {
        if (!"rail".equals(text(trip, "mode"))) return null;

        String currency = text(trip, "currency");
        JsonNode price = trip.path("adultTotalPrice");
        if (!("RON".equals(currency) || "EUR".equals(currency))
                || !price.isNumber() || price.decimalValue().signum() < 0
                || "unavailable".equals(text(trip.path("pricing"), "status"))) {
            return null;
        }

        LocalDateTime departure = dateTime(trip, "departureDate", "departureTime");
        LocalDateTime arrival = dateTime(trip, "arrivalDate", "arrivalTime");
        if (departure == null || arrival == null || !arrival.isAfter(departure)) return null;

        String departureStation = text(trip.path("origin"), "name");
        String arrivalStation = text(trip.path("destination"), "name");
        String carrier = text(trip.path("operator"), "name");
        String departureTime = departure.atZone(BUCHAREST).toOffsetDateTime().toString();
        String arrivalTime = arrival.atZone(BUCHAREST).toOffsetDateTime().toString();
        List<TrainLegResponse> legs = legs(trip.path("legs"));
        TrainJourneyResponse journey = new TrainJourneyResponse(departureStation, arrivalStation,
                null, null, "RO", "RO", departureTime, arrivalTime, carrier, null,
                integer(trip, "durationMinutes"), integer(trip, "transferCount"), null,
                trip.path("separateTickets").asBoolean(false), legs, List.of());

        return new Offer(departure, arrival, price.decimalValue(), currency,
                trip.path("pricing").path("estimated").asBoolean(false),
                partialCatalog, publishedUrl(trip), journey);
    }

    private String publishedUrl(JsonNode trip) {
        String direct = safeUrl(text(trip, "checkoutUrl"), text(trip, "searchUrl"));
        if (direct != null) return direct;

        for (String field : List.of("bookingOptions", "ticketLinks")) {
            JsonNode links = trip.path(field);
            if (!links.isArray()) continue;
            for (JsonNode link : links) {
                String url = link.isTextual() ? safeUrl(link.asText())
                        : safeUrl(text(link, "checkoutUrl"), text(link, "url"));
                if (url != null) return url;
            }
        }
        return null;
    }

    private List<TrainLegResponse> legs(JsonNode raw) {
        if (!raw.isArray()) return List.of();
        List<TrainLegResponse> mapped = new ArrayList<>();
        for (JsonNode leg : raw) {
            mapped.add(new TrainLegResponse(text(leg, "mode"), text(leg.path("operator"), "name"),
                    text(leg, "trainNumber"), text(leg, "departureTime"),
                    text(leg, "arrivalTime"), "Europe/Bucharest", "Europe/Bucharest",
                    integer(leg, "durationMinutes")));
        }
        return List.copyOf(mapped);
    }

    private LocalDateTime dateTime(JsonNode node, String dateField, String timeField) {
        try {
            return LocalDateTime.of(LocalDate.parse(text(node, dateField)),
                    LocalTime.parse(text(node, timeField)));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.canConvertToInt() ? value.intValue() : null;
    }

    private String safeUrl(String... values) {
        for (String value : values) {
            if (value == null) continue;
            try {
                URI uri = URI.create(value);
                if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && uri.getUserInfo() == null && uri.getRawQuery() != null
                        && uri.getRawQuery().toLowerCase().contains("api_key=")) continue;
                if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && uri.getUserInfo() == null) return value;
            } catch (IllegalArgumentException ignored) {
                // Try the next published link.
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
