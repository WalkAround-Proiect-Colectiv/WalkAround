package services.applicationapi.tripplan.configurations;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NewsFeedProperties.class)
public class NewsFeedConfiguration {

    @Bean
    RestClient currentsRestClient(NewsFeedProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.currentsApiTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.currentsApiTimeout());

        return RestClient.builder()
                .baseUrl(properties.currentsApiBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
