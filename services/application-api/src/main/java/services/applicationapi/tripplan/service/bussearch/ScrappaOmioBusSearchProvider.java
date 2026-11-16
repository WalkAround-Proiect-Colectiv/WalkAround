package services.applicationapi.tripplan.service.bussearch;

import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.BusSearchProperties;
import services.applicationapi.tripplan.dto.BusJourneyResponse;
import services.applicationapi.tripplan.dto.BusLegResponse;
import services.applicationapi.tripplan.dto.BusStopResponse;
import services.applicationapi.tripplan.dto.BusTransferResponse;
import services.applicationapi.tripplan.service.exceptions.BusSearchUnavailableException;
import services.applicationapi.tripplan.service.exceptions.InvalidBusSearchException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;

@Component
@ConditionalOnProperty(name = "app.trip-plan.bus-provider", havingValue = "scrappa", matchIfMissing = true)
public class ScrappaOmioBusSearchProvider implements BusSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(ScrappaOmioBusSearchProvider.class);
    private static final int DEFAULT_ADULTS = 1;

    private final RestClient client;
    private final BusSearchProperties properties;

    public ScrappaOmioBusSearchProvider(
            @Qualifier("scrappaRestClient") RestClient client,
            BusSearchProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public long findCityId(String city, String countryCode) {
        if (city.length() < 2) {
            throw new InvalidBusSearchException("City name must contain at least two characters for bus search");
        }

        JsonNode data = data(get("/api/omio/places", Map.of("query", city)));
        JsonNode results = data.path("results");
        if (!results.isArray()) {
            throw unavailable("Scrappa places response has no results array");
        }

        Set<String> keys = CityNameNormalizer.toComparisonKeys(city);
        for (JsonNode place : results) {
            String placeName = text(place, "name");
            if ("location".equals(text(place, "type"))
                    && countryCode.equalsIgnoreCase(text(place, "country_code"))
                    && placeName != null
                    && CityNameNormalizer.toComparisonKeys(placeName).stream().anyMatch(keys::contains)
                    && place.path("position_id").canConvertToLong()) {
                return place.path("position_id").longValue();
            }
        }

        throw new InvalidBusSearchException("Selected city is not available in Omio");
    }

    @Override
    public SearchPage search(long originId, long destinationId, LocalDate date,
                             LocalDate returnDate, int page) {
        JsonNode response;
        try {
            response = client.get()
                    .uri(builder -> {
                        builder.path("/api/omio/search")
                                .queryParam("origin_id", originId)
                                .queryParam("destination_id", destinationId)
                                .queryParam("date", date)
                                .queryParam("travel_mode", "bus")
                                .queryParam("currency", "EUR")
                                .queryParam("sort", "price")
                                .queryParam("adults", DEFAULT_ADULTS)
                                .queryParam("limit", 100)
                                .queryParam("page", page);
                        if (returnDate != null) {
                            builder.queryParam("return_date", returnDate);
                        }
                        return builder.build();
                    })
                    .header("x-api-key", apiKey())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Scrappa Omio search failed: {}", exception.getClass().getSimpleName());
            throw new BusSearchUnavailableException("Bus search is temporarily unavailable", exception);
        }

        JsonNode data = data(response);
        JsonNode results = data.path("results");
        if (!results.isArray()) {
            throw unavailable("Scrappa search response has no results array");
        }

        String searchId = text(data.path("search_meta"), "search_id");
        List<BusOffer> offers = new ArrayList<>();
        for (JsonNode result : results) {
            if (!"bus".equals(text(result, "mode"))
                    || !result.path("is_bookable").asBoolean(false)
                    || !validEuroPrice(result.path("price"))) {
                continue;
            }

            LocalDateTime departure = localTime(text(result, "departure_time"));
            LocalDateTime arrival = localTime(text(result, "arrival_time"));
            BusJourneyResponse journey = journey(result);
            if (departure != null && arrival != null && journey != null) {
                offers.add(new BusOffer(text(result, "trip_id"),
                        result.path("price").path("amount_minor").longValue(),
                        departure, arrival, journey));
            }
        }

        log.debug("Scrappa returned {} bookable bus offers on {} page {}", offers.size(), date, page);
        return new SearchPage(searchId, List.copyOf(offers),
                data.path("pagination").path("has_more").asBoolean(false));
    }

    @Override
    public List<ReturnOffer> returns(String searchId, String tripId) {
        if (searchId == null || searchId.isBlank() || tripId == null || tripId.isBlank()) {
            return List.of();
        }

        JsonNode data = data(get("/api/omio/trip", Map.of("search_id", searchId, "trip_id", tripId)));
        JsonNode options = data.path("return_options");
        if (!options.isArray()) options = data.path("trip").path("return_options");
        if (!options.isArray()) {
            return List.of();
        }

        List<ReturnOffer> results = new ArrayList<>();
        for (JsonNode option : options) {
            JsonNode price = option.path("round_trip_price");
            JsonNode returnTrip = option.path("trip");
            if (!returnTrip.isObject()) returnTrip = option.path("return_trip");
            if (!returnTrip.isObject()) returnTrip = option;
            BusJourneyResponse journey = journey(returnTrip);
            if (validEuroPrice(price) && journey != null) {
                results.add(new ReturnOffer(price.path("amount_minor").longValue(), journey));
            }
        }
        return results;
    }

    @Override
    public BusStopResponse findStop(long positionId) {
        JsonNode data = data(get("/api/omio/places", Map.of("id", positionId)));
        JsonNode places = data.path("results");
        if (places.isArray()) {
            for (JsonNode place : places) {
                if (place.path("position_id").canConvertToLong()
                        && place.path("position_id").longValue() == positionId) {
                    return stop(place);
                }
            }
        }

        for (JsonNode candidate : List.of(data.path("place"), data.path("result"), data)) {
            if (candidate.path("position_id").canConvertToLong()
                    && candidate.path("position_id").longValue() == positionId) {
                return stop(candidate);
            }
        }
        log.warn("Scrappa did not resolve transfer position {}", positionId);
        return null;
    }

    private JsonNode get(String path, Map<String, ?> query) {
        try {
            return client.get()
                    .uri(builder -> {
                        builder.path(path);
                        query.forEach((key, value) -> builder.queryParam(key, value));
                        return builder.build();
                    })
                    .header("x-api-key", apiKey())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Scrappa Omio request failed: {}", exception.getClass().getSimpleName());
            throw new BusSearchUnavailableException("Bus search is temporarily unavailable", exception);
        }
    }

    private String apiKey() {
        if (properties.scrappaApiKey() == null || properties.scrappaApiKey().isBlank()) {
            throw new BusSearchUnavailableException("Bus search is temporarily unavailable");
        }
        return properties.scrappaApiKey();
    }

    private JsonNode data(JsonNode response) {
        if (response == null || !response.path("success").asBoolean(false)
                || !response.path("data").isObject()) {
            throw unavailable("Scrappa returned an unsuccessful response");
        }
        return response.path("data");
    }

    private BusSearchUnavailableException unavailable(String message) {
        log.warn(message);
        return new BusSearchUnavailableException("Bus search is temporarily unavailable");
    }

    private boolean validEuroPrice(JsonNode price) {
        return "EUR".equals(text(price, "currency"))
                && price.path("amount_minor").canConvertToLong()
                && price.path("amount_minor").longValue() >= 0;
    }

    private BusJourneyResponse journey(JsonNode trip) {
        String departureTime = text(trip, "departure_time");
        String arrivalTime = text(trip, "arrival_time");
        if (localTime(departureTime) == null || localTime(arrivalTime) == null) {
            return null;
        }

        List<BusLegResponse> legs = new ArrayList<>();
        JsonNode rawLegs = trip.path("legs");
        if (rawLegs.isArray()) {
            for (JsonNode leg : rawLegs) {
                legs.add(new BusLegResponse(text(leg, "mode"), text(leg, "company"),
                        text(leg, "line_number"), text(leg, "departure_time"),
                        text(leg, "arrival_time"), text(leg, "departure_zone"),
                        text(leg, "arrival_zone"), integer(leg, "duration_minutes")));
            }
        }

        List<BusTransferResponse> transfers = new ArrayList<>();
        JsonNode rawChanges = trip.path("changes");
        if (rawChanges.isArray()) {
            for (JsonNode change : rawChanges) {
                transfers.add(new BusTransferResponse(text(change, "type"),
                        integer(change, "after_leg"), integer(change, "duration_minutes"),
                        stopId(change, "from_position_id"), stopId(change, "to_position_id")));
            }
        }

        JsonNode departure = trip.path("departure");
        JsonNode arrival = trip.path("arrival");
        JsonNode splitTicket = trip.path("is_split_ticket");

        return new BusJourneyResponse(text(departure, "name"),
                text(arrival, "name"), text(departure, "city"), text(arrival, "city"),
                text(departure, "country_code"), text(arrival, "country_code"),
                departureTime, arrivalTime, text(trip.path("provider"), "name"),
                safeLogoUrl(text(trip.path("provider"), "logo_url")),
                integer(trip, "duration_minutes"), integer(trip, "stops"),
                integer(trip, "tickets_left"),
                splitTicket.isBoolean() ? splitTicket.asBoolean() : null,
                List.copyOf(legs), List.copyOf(transfers));
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() ? value.intValue() : null;
    }

    private BusStopResponse stopId(JsonNode node, String field) {
        JsonNode id = node.path(field);
        return id.canConvertToLong() && id.longValue() > 0
                ? new BusStopResponse(id.longValue(), null, null, null) : null;
    }

    private BusStopResponse stop(JsonNode place) {
        return new BusStopResponse(place.path("position_id").longValue(),
                text(place, "name"), text(place, "city_name") != null
                        ? text(place, "city_name") : text(place, "city"),
                text(place, "country_code"));
    }

    private String safeLogoUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && "static.omio.design".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null ? value : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private LocalDateTime localTime(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.textValue() : null;
    }
}
