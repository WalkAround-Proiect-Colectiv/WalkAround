package services.applicationapi.tripplan.configurations;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CityAutocompleteProperties.class)
public class CityAutocompleteConfiguration {

    @Bean
    RestClient geoNamesRestClient(CityAutocompleteProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.geonamesTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.geonamesTimeout());

        return RestClient.builder()
                .baseUrl(properties.geonamesBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
