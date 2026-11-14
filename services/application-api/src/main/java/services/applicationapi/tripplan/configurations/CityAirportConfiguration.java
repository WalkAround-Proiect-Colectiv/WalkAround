package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CityAirportProperties.class)
public class CityAirportConfiguration {
}
