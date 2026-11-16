package services.applicationapi.tripplan.configurations;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TrainSearchProperties.class)
public class TrainSearchConfiguration {

    @Bean
    RestClient scrappaTrainRestClient(TrainSearchProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.scrappaTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.scrappaTimeout());

        return RestClient.builder()
                .baseUrl(properties.scrappaBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
