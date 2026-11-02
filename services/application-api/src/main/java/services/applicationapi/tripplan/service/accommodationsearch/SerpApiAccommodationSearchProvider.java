package services.applicationapi.tripplan.service.accommodationsearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import services.applicationapi.tripplan.configurations.AccommodationSearchProperties;
import services.applicationapi.tripplan.service.exceptions.AccommodationSearchUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.accommodation-provider", havingValue = "serpapi", matchIfMissing = true)
public class SerpApiAccommodationSearchProvider implements AccommodationSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(SerpApiAccommodationSearchProvider.class);
    private static final int ADULTS = 1;

    private final RestClient restClient;
    private final AccommodationSearchProperties properties;

    public SerpApiAccommodationSearchProvider(
            @Qualifier("serpApiRestClient") RestClient restClient,
            AccommodationSearchProperties properties
    ) {
        if (properties.serpApiKey() == null || properties.serpApiKey().isBlank()) {
            throw new IllegalArgumentException("SERP_API_KEY must be configured");
        }

        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<AccommodationOffer> search(String destination, LocalDate checkInDate, LocalDate checkOutDate) {
        SerpApiResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/search.json")
                            .queryParam("engine", "google_hotels")
                            .queryParam("q", "{destination}")
                            .queryParam("check_in_date", checkInDate)
                            .queryParam("check_out_date", checkOutDate)
                            .queryParam("currency", "EUR")
                            .queryParam("adults", ADULTS)
                            .queryParam("sort_by", 3)
                            .queryParam("api_key", "{apiKey}")
                            .build(Map.of(
                                    "destination", destination,
                                    "apiKey", properties.serpApiKey()
                            )))
                    .retrieve()
                    .body(SerpApiResponse.class);
        } catch (RestClientException exception) {
            log.warn("SerpApi accommodation search failed: {}", exception.getClass().getSimpleName());
            throw new AccommodationSearchUnavailableException("Accommodation search is temporarily unavailable", exception);
        }

        requireSuccess(response == null ? null : response.searchMetadata());

        if (response.properties() == null) {
            log.debug("SerpApi accommodation search returned no properties");
            return List.of();
        }

        List<AccommodationOffer> offers = response.properties().stream()
                .filter(Objects::nonNull)
                .filter(property -> property.name() != null && !property.name().isBlank())
                .filter(property -> property.totalRate() != null)
                .filter(property -> property.totalRate().afterTaxesFees() != null
                        && property.totalRate().afterTaxesFees().signum() > 0)
                .filter(property -> property.propertyToken() != null && !property.propertyToken().isBlank())
                .map(property -> new AccommodationOffer(
                        property.name(),
                        property.type(),
                        property.totalRate().afterTaxesFees(),
                        safeExternalUrl(property.link()),
                        property.propertyToken()
                ))
                .toList();

        log.debug("SerpApi returned {} accommodation offers with complete total prices", offers.size());
        return offers;
    }

    @Override
    public AccommodationDetails details(String propertyToken, LocalDate checkInDate, LocalDate checkOutDate) {
        SerpApiDetailsResponse response;

        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/search.json")
                            .queryParam("engine", "google_hotels")
                            .queryParam("property_token", "{propertyToken}")
                            .queryParam("check_in_date", checkInDate)
                            .queryParam("check_out_date", checkOutDate)
                            .queryParam("currency", "EUR")
                            .queryParam("adults", ADULTS)
                            .queryParam("api_key", "{apiKey}")
                            .build(Map.of(
                                    "propertyToken", propertyToken,
                                    "apiKey", properties.serpApiKey()
                            )))
                    .retrieve()
                    .body(SerpApiDetailsResponse.class);
        } catch (RestClientException exception) {
            log.warn("SerpApi accommodation details request failed: {}", exception.getClass().getSimpleName());
            throw new AccommodationSearchUnavailableException("Accommodation search is temporarily unavailable", exception);
        }

        requireSuccess(response == null ? null : response.searchMetadata());

        List<String> amenities = response.amenities() == null
                ? List.of()
                : response.amenities().stream()
                        .filter(Objects::nonNull)
                        .filter(amenity -> !amenity.isBlank())
                        .toList();

        List<BookingOption> bookingOptions = response.prices() == null
                ? List.of()
                : response.prices().stream()
                        .filter(Objects::nonNull)
                        .filter(price -> price.source() != null && !price.source().isBlank())
                        .filter(price -> price.totalRate() != null
                                && price.totalRate().afterTaxesFees() != null
                                && price.totalRate().afterTaxesFees().signum() > 0)
                        .map(price -> new BookingOption(
                                price.source(),
                                price.totalRate().afterTaxesFees(),
                                safeExternalUrl(price.link())
                        ))
                        .filter(option -> option.bookingUrl() != null)
                        .toList();

        List<String> roomTypes = response.featuredPrices() == null
                ? List.of()
                : response.featuredPrices().stream()
                        .filter(Objects::nonNull)
                        .filter(price -> price.rooms() != null)
                        .flatMap(price -> price.rooms().stream())
                        .filter(Objects::nonNull)
                        .map(Room::name)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .limit(properties.accommodationSearchMaxResults())
                        .toList();

        log.debug("SerpApi returned {} booking options for an accommodation", bookingOptions.size());
        return new AccommodationDetails(
                response.totalRate() == null ? null : response.totalRate().afterTaxesFees(),
                response.description(),
                response.address(),
                response.overallRating(),
                response.reviews(),
                amenities,
                roomTypes,
                bookingOptions
        );
    }

    private void requireSuccess(SearchMetadata metadata) {
        if (metadata == null) {
            log.warn("SerpApi returned accommodation response without search metadata");
            throw new AccommodationSearchUnavailableException("Accommodation search is temporarily unavailable");
        }

        if (!"Success".equals(metadata.status())) {
            log.warn("SerpApi accommodation request did not succeed (status={})", metadata.status());
            throw new AccommodationSearchUnavailableException("Accommodation search is temporarily unavailable");
        }
    }

    private String safeExternalUrl(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(link);
            String query = uri.getRawQuery();
            if ("https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && (query == null || !query.toLowerCase(Locale.ROOT).contains("api_key"))) {
                return uri.toString();
            }
        } catch (IllegalArgumentException exception) {
            log.debug("SerpApi returned an invalid accommodation link");
        }

        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiResponse(
            @JsonProperty("search_metadata") SearchMetadata searchMetadata,
            List<Property> properties
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchMetadata(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Property(
            String name,
            String type,
            String link,
            @JsonProperty("property_token") String propertyToken,
            @JsonProperty("total_rate") TotalRate totalRate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SerpApiDetailsResponse(
            @JsonProperty("search_metadata") SearchMetadata searchMetadata,
            String description,
            String address,
            @JsonProperty("overall_rating") BigDecimal overallRating,
            Integer reviews,
            List<String> amenities,
            @JsonProperty("total_rate") TotalRate totalRate,
            @JsonProperty("featured_prices") List<FeaturedPrice> featuredPrices,
            List<PriceSource> prices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PriceSource(
            String source,
            String link,
            @JsonProperty("total_rate") TotalRate totalRate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FeaturedPrice(List<Room> rooms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Room(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TotalRate(
            @JsonProperty("extracted_after_taxes_fees") BigDecimal afterTaxesFees
    ) {
    }
}
