package services.applicationapi.tripplan;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import services.applicationapi.tripplan.configurations.CityAutocompleteProperties;
import services.applicationapi.tripplan.service.cityautocomplete.GeoNamesCitySearchProvider;
import services.applicationapi.tripplan.service.exceptions.CitySearchUnavailableException;

class GeoNamesCitySearchProviderTest {
    private static final String BASE_URL = "https://secure.geonames.org";
    private MockRestServiceServer server;
    private GeoNamesCitySearchProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeoNamesCitySearchProvider(builder.build(),
                new CityAutocompleteProperties("configured-user", 10, URI.create(BASE_URL), Duration.ofSeconds(1)));
    }

    @Test
    void unauthorizedGeoNamesAccountReturnsActionableConfigurationGuidance() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/searchJSON")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> provider.searchByPrefix("Paris", 10))
                .isInstanceOf(CitySearchUnavailableException.class)
                .hasMessage("GeoNames rejected the configured account. Check GEONAMES_USERNAME and enable web services for that account.");

        server.verify();
    }
}
