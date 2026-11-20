package services.applicationapi.tripplan.configurations;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ExchangeRateProperties.class)
public class ExchangeRateConfiguration {

    @Bean
    RestClient frankfurterRestClient(ExchangeRateProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.frankfurterTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.frankfurterTimeout());

        return RestClient.builder()
                .baseUrl(properties.frankfurterBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
