package services.applicationapi.recommendation.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfiguration {

    @Bean
    RestClient recommendationOpenMeteoRestClient(RecommendationProperties properties) {
        Duration timeout = properties.resolvedOpenMeteoTimeout();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .baseUrl(properties.resolvedOpenMeteoBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
