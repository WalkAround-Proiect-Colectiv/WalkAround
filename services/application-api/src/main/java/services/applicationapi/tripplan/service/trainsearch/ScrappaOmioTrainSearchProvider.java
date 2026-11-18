package services.applicationapi.tripplan.service.trainsearch;

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
import services.applicationapi.tripplan.configurations.TrainSearchProperties;
import services.applicationapi.tripplan.dto.TrainJourneyResponse;
import services.applicationapi.tripplan.dto.TrainLegResponse;
import services.applicationapi.tripplan.dto.TrainStopResponse;
import services.applicationapi.tripplan.dto.TrainTransferResponse;
import services.applicationapi.tripplan.service.exceptions.InvalidTrainSearchException;
import services.applicationapi.tripplan.service.exceptions.TrainSearchUnavailableException;
import services.applicationapi.tripplan.service.utils.CityNameNormalizer;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(name = "app.trip-plan.train-provider", havingValue = "scrappa", matchIfMissing = true)
public class ScrappaOmioTrainSearchProvider implements TrainSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(ScrappaOmioTrainSearchProvider.class);

    private final RestClient client;
    private final TrainSearchProperties properties;

    public ScrappaOmioTrainSearchProvider(
            @Qualifier("scrappaTrainRestClient") RestClient client,
            TrainSearchProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public long findCityId(String city, String countryCode) {
        if (city.length() < 2) {
            throw new InvalidTrainSearchException("City name must contain at least two characters");
        }

        JsonNode results = data(get("/api/omio/places", Map.of("query", city))).path("results");
        if (!results.isArray()) throw unavailable("Scrappa places response has no results array");

        Set<String> keys = CityNameNormalizer.toComparisonKeys(city);
        for (JsonNode place : results) {
            String name = text(place, "name");
            if ("location".equals(text(place, "type"))
                    && countryCode.equalsIgnoreCase(text(place, "country_code"))
                    && name != null
                    && CityNameNormalizer.toComparisonKeys(name).stream().anyMatch(keys::contains)
                    && place.path("position_id").canConvertToLong()) {
                return place.path("position_id").longValue();
            }
        }
        throw new InvalidTrainSearchException("Selected city is not available in Omio");
    }

    @Override
    public SearchPage search(long originId, long destinationId, LocalDate date,
                             LocalDate returnDate, int page, int adults) {
        JsonNode response;
        try {
            response = client.get()
                    .uri(builder -> {
                        builder.path("/api/omio/search")
                                .queryParam("origin_id", originId)
                                .queryParam("destination_id", destinationId)
                                .queryParam("date", date)
                                .queryParam("travel_mode", "train")
                                .queryParam("currency", "EUR")
                                .queryParam("sort", "price")
                                .queryParam("adults", adults)
                                .queryParam("limit", 100)
                                .queryParam("page", page);
                        if (returnDate != null) builder.queryParam("return_date", returnDate);
                        return builder.build();
                    })
                    .header("x-api-key", apiKey())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException exception) {
            log.warn("Scrappa Omio train search failed: {}", exception.getClass().getSimpleName());
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable", exception);
        }

        JsonNode data = data(response);
        JsonNode results = data.path("results");
        if (!results.isArray()) throw unavailable("Scrappa train response has no results array");

        List<TrainOffer> offers = new ArrayList<>();
        for (JsonNode result : results) {
            if (!"train".equals(text(result, "mode"))
                    || !result.path("is_bookable").asBoolean(false)
                    || !validEuroPrice(result.path("price"))) continue;

            LocalDateTime departure = localTime(text(result, "departure_time"));
            LocalDateTime arrival = localTime(text(result, "arrival_time"));
            TrainJourneyResponse journey = journey(result);
            if (departure != null && arrival != null && journey != null) {
                offers.add(new TrainOffer(text(result, "trip_id"),
                        result.path("price").path("amount_minor").longValue(),
                        departure, arrival, journey));
            }
        }

        log.debug("Scrappa returned {} bookable train offers on {} page {}", offers.size(), date, page);
        return new SearchPage(text(data.path("search_meta"), "search_id"),
                List.copyOf(offers), data.path("pagination").path("has_more").asBoolean(false));
    }

    @Override
    public List<ReturnOffer> returns(String searchId, String tripId) {
        if (searchId == null || searchId.isBlank() || tripId == null || tripId.isBlank()) {
            return List.of();
        }

        JsonNode data = data(get("/api/omio/trip", Map.of("search_id", searchId, "trip_id", tripId)));
        JsonNode options = data.path("return_options");
        if (!options.isArray()) options = data.path("trip").path("return_options");
        if (!options.isArray()) return List.of();

        List<ReturnOffer> results = new ArrayList<>();
        for (JsonNode option : options) {
            JsonNode price = option.path("round_trip_price");
            JsonNode returnTrip = option.path("trip");
            if (!returnTrip.isObject()) returnTrip = option.path("return_trip");
            if (!returnTrip.isObject()) returnTrip = option;
            TrainJourneyResponse journey = journey(returnTrip);
            if (validEuroPrice(price) && journey != null) {
                results.add(new ReturnOffer(price.path("amount_minor").longValue(), journey));
            }
        }
        return results;
    }

    @Override
    public TrainStopResponse findStop(long positionId) {
        JsonNode data = data(get("/api/omio/places", Map.of("id", positionId)));
        JsonNode place = matchingPlace(data, positionId);
        if (place == null) {
            log.warn("Scrappa did not resolve train transfer position {}", positionId);
            return null;
        }
        return stop(place);
    }

    private JsonNode matchingPlace(JsonNode node, long positionId) {
        for (String field : List.of("results", "place", "result", "children")) {
            JsonNode nested = node.path(field);
            if (nested.isArray()) {
                for (JsonNode child : nested) {
                    JsonNode match = matchingPlace(child, positionId);
                    if (match != null) return match;
                }
            } else if (nested.isObject()) {
                JsonNode match = matchingPlace(nested, positionId);
                if (match != null) return match;
            }
        }
        return node.path("position_id").canConvertToLong()
                && node.path("position_id").longValue() == positionId ? node : null;
    }

    private TrainJourneyResponse journey(JsonNode trip) {
        String departureTime = text(trip, "departure_time");
        String arrivalTime = text(trip, "arrival_time");
        if (localTime(departureTime) == null || localTime(arrivalTime) == null) return null;

        List<TrainLegResponse> legs = new ArrayList<>();
        JsonNode rawLegs = trip.path("legs");
        if (rawLegs.isArray()) {
            for (JsonNode leg : rawLegs) {
                legs.add(new TrainLegResponse(text(leg, "mode"), text(leg, "company"),
                        text(leg, "line_number"), text(leg, "departure_time"),
                        text(leg, "arrival_time"), text(leg, "departure_zone"),
                        text(leg, "arrival_zone"), integer(leg, "duration_minutes")));
            }
        }

        List<TrainTransferResponse> transfers = new ArrayList<>();
        JsonNode rawChanges = trip.path("changes");
        if (rawChanges.isArray()) {
            for (JsonNode change : rawChanges) {
                transfers.add(new TrainTransferResponse(text(change, "type"),
                        integer(change, "after_leg"), integer(change, "duration_minutes"),
                        stopId(change, "from_position_id"), stopId(change, "to_position_id")));
            }
        }

        JsonNode departure = trip.path("departure");
        JsonNode arrival = trip.path("arrival");
        JsonNode splitTicket = trip.path("is_split_ticket");
        return new TrainJourneyResponse(text(departure, "name"), text(arrival, "name"),
                text(departure, "city"), text(arrival, "city"),
                text(departure, "country_code"), text(arrival, "country_code"),
                departureTime, arrivalTime, text(trip.path("provider"), "name"),
                safeLogoUrl(text(trip.path("provider"), "logo_url")),
                integer(trip, "duration_minutes"), integer(trip, "stops"),
                integer(trip, "tickets_left"),
                splitTicket.isBoolean() ? splitTicket.asBoolean() : null,
                List.copyOf(legs), List.copyOf(transfers));
    }

    private TrainStopResponse stopId(JsonNode node, String field) {
        JsonNode id = node.path(field);
        return id.canConvertToLong() && id.longValue() > 0
                ? new TrainStopResponse(id.longValue(), null, null, null) : null;
    }

    private TrainStopResponse stop(JsonNode place) {
        return new TrainStopResponse(place.path("position_id").longValue(),
                text(place, "name") != null ? text(place, "name") : text(place, "default_name"),
                text(place, "city_name") != null ? text(place, "city_name") : text(place, "city"),
                text(place, "country_code"));
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() ? value.intValue() : null;
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

    private boolean validEuroPrice(JsonNode price) {
        return "EUR".equals(text(price, "currency"))
                && price.path("amount_minor").canConvertToLong()
                && price.path("amount_minor").longValue() >= 0;
    }

    private LocalDateTime localTime(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException exception) {
            return null;
        }
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
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable", exception);
        }
    }

    private String apiKey() {
        if (properties.scrappaApiKey() == null || properties.scrappaApiKey().isBlank()) {
            throw new TrainSearchUnavailableException("Train search is temporarily unavailable");
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

    private TrainSearchUnavailableException unavailable(String message) {
        log.warn(message);
        return new TrainSearchUnavailableException("Train search is temporarily unavailable");
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }
}
