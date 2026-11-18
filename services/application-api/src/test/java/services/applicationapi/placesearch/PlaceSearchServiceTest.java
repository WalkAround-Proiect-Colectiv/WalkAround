package services.applicationapi.placesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import services.applicationapi.placesearch.config.PlaceSearchProperties;
import services.applicationapi.placesearch.dto.PlaceSearchResult;
import services.applicationapi.placesearch.service.InvalidPlaceSearchException;
import services.applicationapi.placesearch.service.PlaceSearchProvider;
import services.applicationapi.placesearch.service.PlaceSearchService;

class PlaceSearchServiceTest {

    private static final PlaceSearchProperties PROPERTIES = new PlaceSearchProperties(
            "photon",
            URI.create("https://photon.komoot.io"),
            Duration.ofSeconds(10),
            5
    );

    @Test
    void delegatesReverseLookupToProvider() {
        PlaceSearchProvider provider = new PlaceSearchProvider() {
            @Override
            public List<PlaceSearchResult> search(String query, double latitude, double longitude, int zoom, int limit) {
                return List.of();
            }

            @Override
            public PlaceSearchResult reverse(double latitude, double longitude) {
                return new PlaceSearchResult("Cluj-Napoca", "Romania", latitude, longitude);
            }
        };

        PlaceSearchResult result = new PlaceSearchService(provider, PROPERTIES).reverse(46.7712, 23.5899);

        assertThat(result.name()).isEqualTo("Cluj-Napoca");
        assertThat(result.latitude()).isEqualTo(46.7712);
        assertThat(result.longitude()).isEqualTo(23.5899);
    }

    @Test
    void rejectsInvalidReverseCoordinates() {
        PlaceSearchProvider provider = new PlaceSearchProvider() {
            @Override
            public List<PlaceSearchResult> search(String query, double latitude, double longitude, int zoom, int limit) {
                return List.of();
            }

            @Override
            public PlaceSearchResult reverse(double latitude, double longitude) {
                throw new AssertionError("provider should not be called");
            }
        };

        PlaceSearchService service = new PlaceSearchService(provider, PROPERTIES);

        assertThatThrownBy(() -> service.reverse(91, 23))
                .isInstanceOf(InvalidPlaceSearchException.class);
    }
}
