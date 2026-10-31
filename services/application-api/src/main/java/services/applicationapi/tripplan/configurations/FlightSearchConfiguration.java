package services.applicationapi.tripplan.configurations;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FlightSearchProperties.class)
public class FlightSearchConfiguration {

    @Bean
    RestClient serpApiRestClient(FlightSearchProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.serpApiTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.serpApiTimeout());

        return RestClient.builder()
                .baseUrl(properties.serpApiBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
