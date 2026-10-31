package services.applicationapi.tripplan.service.flightsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.FlightSearchProperties;
import services.applicationapi.tripplan.dto.FlightSegmentResponse;
import services.applicationapi.tripplan.service.exceptions.FlightSearchUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.flight-provider", havingValue = "serpapi", matchIfMissing = true)
public class SerpApiFlightSearchProvider implements FlightSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SerpApiFlightSearchProvider.class);
    private static final String CURRENCY = "EUR";
    private static final String GOOGLE_FLIGHTS_HOME = "https://www.google.com/travel/flights";

    private final RestClient restClient;
    private final FlightSearchProperties properties;

    public SerpApiFlightSearchProvider(
            @Qualifier("serpApiRestClient") RestClient restClient,
            FlightSearchProperties properties
    ) {
        if (properties.serpApiKey() == null || properties.serpApiKey().isBlank()) {
            throw new IllegalArgumentException("SERP_API_KEY must be configured");
        }

        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<FlightOffer> searchOneWay(
            List<String> departureAirports,
            List<String> arrivalAirports,
            LocalDate date,
            BigDecimal maxPrice,
            LocalTime earliestDeparture,
            LocalTime latestDeparture
    ) {
        return search(2, departureAirports, arrivalAirports, date, null,
                maxPrice, null, earliestDeparture, latestDeparture);
    }

    @Override
    public List<FlightOffer> searchRoundTrip(
            String departureAirport,
            List<String> arrivalAirports,
            LocalDate outboundDate,
            LocalDate returnDate,
            BigDecimal maxPrice,
            LocalTime earliestDeparture,
            LocalTime latestDeparture
    ) {
        return search(1, List.of(departureAirport), arrivalAirports,
                outboundDate, returnDate, maxPrice, null, earliestDeparture, latestDeparture);
    }

    @Override
    public List<FlightOffer> searchReturns(
            String departureAirport,
            List<String> arrivalAirports,
            LocalDate outboundDate,
            LocalDate returnDate,
            BigDecimal maxPrice,
            String departureToken
    ) {
        return search(1, List.of(departureAirport), arrivalAirports,
                outboundDate, returnDate, maxPrice, departureToken, null, null);
    }

    private List<FlightOffer> search(
            int type,
            List<String> departureAirports,
            List<String> arrivalAirports,
            LocalDate outboundDate,
            LocalDate returnDate,
            BigDecimal maxPrice,
            String departureToken,
            LocalTime earliestDeparture,
            LocalTime latestDeparture
    ) {
        SerpApiResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> {
                        builder.path("/search.json")
                                .queryParam("engine", "google_flights")
                                .queryParam("type", type)
                                .queryParam("departure_id", String.join(",", departureAirports))
                                .queryParam("arrival_id", String.join(",", arrivalAirports))
                                .queryParam("outbound_date", outboundDate)
                                .queryParam("currency", CURRENCY)
                                .queryParam("max_price", maxPrice.toBigInteger())
                                .queryParam("api_key", "{apiKey}");

                        if (returnDate != null) {
                            builder.queryParam("return_date", returnDate);
                        }

                        if (departureToken != null) {
                            builder.queryParam("departure_token", "{departureToken}");
                        }

                        if (earliestDeparture != null && latestDeparture != null) {
                            builder.queryParam("outbound_times",
                                    earliestDeparture.getHour() + "," + latestDeparture.getHour());
                        }

                        return builder.build(Map.of(
                                "apiKey", properties.serpApiKey(),
                                "departureToken", departureToken == null ? "" : departureToken
                        ));
                    })
                    .retrieve()
                    .body(SerpApiResponse.class);
        } catch (RestClientException exception) {
            log.warn("SerpApi flight search failed: {}", exception.getClass().getSimpleName());
            throw new FlightSearchUnavailableException("Flight search is temporarily unavailable", exception);
        }

        if (response == null || response.error() != null) {
            log.warn("SerpApi returned an invalid flight search response");
            throw new FlightSearchUnavailableException("Flight search is temporarily unavailable");
        }

        String viewUrl = googleFlightsUrl(response.searchMetadata());
        List<FlightOffer> offers = new ArrayList<>();
        addOffers(offers, response.bestFlights(), viewUrl);
        addOffers(offers, response.otherFlights(), viewUrl);

        log.debug("SerpApi returned {} flight offers", offers.size());
        return offers;
    }

    private String googleFlightsUrl(SerpApiSearchMetadata metadata) {
        if (metadata == null || metadata.googleFlightsUrl() == null) {
            return GOOGLE_FLIGHTS_HOME;
        }

        try {
            URI url = URI.create(metadata.googleFlightsUrl());
            String query = url.getRawQuery();

            if ("https".equalsIgnoreCase(url.getScheme())
                    && "www.google.com".equalsIgnoreCase(url.getHost())
                    && url.getPath() != null
                    && (url.getPath().equals("/travel/flights")
                            || url.getPath().startsWith("/travel/flights/"))
                    && url.getUserInfo() == null
                    && url.getPort() == -1
                    && (query == null || !query.toLowerCase(Locale.ROOT).contains("api_key"))) {
                return url.toString();
            }
        } catch (IllegalArgumentException exception) {
            log.debug("SerpApi supplied an invalid Google Flights URL");
        }

        log.warn("SerpApi supplied an unexpected Google Flights URL; using the Google Flights homepage");
        return GOOGLE_FLIGHTS_HOME;
    }

    private void addOffers(List<FlightOffer> offers, List<SerpApiOffer> results, String viewUrl) {
        if (results == null) {
            return;
        }

        for (SerpApiOffer result : results) {
            if (result.price() == null || result.flights() == null || result.flights().isEmpty()) {
                continue;
            }

            List<FlightSegmentResponse> segments = new ArrayList<>();

            for (SerpApiFlight flight : result.flights()) {
                if (flight.departureAirport() == null || flight.arrivalAirport() == null) {
                    segments.clear();
                    break;
                }

                segments.add(new FlightSegmentResponse(
                        flight.departureAirport().id(),
                        flight.departureAirport().time(),
                        flight.arrivalAirport().id(),
                        flight.arrivalAirport().time(),
                        flight.flightNumber(),
                        flight.airline()
                ));
            }

            if (!segments.isEmpty()) {
                offers.add(new FlightOffer(
                        result.price(),
                        List.copyOf(segments),
                        result.departureToken(),
                        viewUrl
                ));
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiResponse(
            @JsonProperty("search_metadata") SerpApiSearchMetadata searchMetadata,
            @JsonProperty("best_flights") List<SerpApiOffer> bestFlights,
            @JsonProperty("other_flights") List<SerpApiOffer> otherFlights,
            String error
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiSearchMetadata(
            @JsonProperty("google_flights_url") String googleFlightsUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiOffer(
            BigDecimal price,
            List<SerpApiFlight> flights,
            @JsonProperty("departure_token") String departureToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiFlight(
            @JsonProperty("departure_airport") SerpApiAirport departureAirport,
            @JsonProperty("arrival_airport") SerpApiAirport arrivalAirport,
            @JsonProperty("flight_number") String flightNumber,
            String airline
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiAirport(String id, String time) {
    }
}
