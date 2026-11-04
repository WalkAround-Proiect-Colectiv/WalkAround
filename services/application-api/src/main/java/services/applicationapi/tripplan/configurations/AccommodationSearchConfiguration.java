package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccommodationSearchProperties.class)
public class AccommodationSearchConfiguration {
}
