package services.applicationapi.events.configurations;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EventsProperties.class)
public class EventsConfiguration {

    @Bean
    Clock eventsClock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient eventsSerpApiRestClient(EventsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.serpApiBaseUrl().toString())
                .requestFactory(requestFactory(properties.serpApiTimeout()))
                .build();
    }

    @Bean
    RestClient eventsSerpApiDetailsRestClient(EventsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.serpApiBaseUrl().toString())
                .requestFactory(requestFactory(properties.detailsTimeout()))
                .build();
    }

    @Bean
    RestClient nominatimRestClient(EventsProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.nominatimBaseUrl().toString())
                .defaultHeader(HttpHeaders.USER_AGENT, properties.nominatimUserAgent())
                .requestFactory(requestFactory(properties.nominatimTimeout()))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        return factory;
    }
}
