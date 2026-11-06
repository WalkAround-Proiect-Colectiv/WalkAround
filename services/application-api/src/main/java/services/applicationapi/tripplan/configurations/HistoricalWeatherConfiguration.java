package services.applicationapi.tripplan.configurations;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(HistoricalWeatherProperties.class)
public class HistoricalWeatherConfiguration {

    @Bean
    RestClient openMeteoArchiveRestClient(
            HistoricalWeatherProperties archiveProperties,
            WeatherForecastProperties forecastProperties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(forecastProperties.openMeteoTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(forecastProperties.openMeteoTimeout());

        return RestClient.builder()
                .baseUrl(archiveProperties.openMeteoArchiveBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
