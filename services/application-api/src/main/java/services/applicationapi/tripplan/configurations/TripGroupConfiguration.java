package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TripGroupProperties.class)
public class TripGroupConfiguration {
}
