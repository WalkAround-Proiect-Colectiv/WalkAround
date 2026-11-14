package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.trip-plan")
public record CityAirportProperties(
        @DefaultValue("100") int nearbyAirportRadiusKm,
        @DefaultValue("5") int nearbyAirportMaxResults
) {

    public CityAirportProperties {
        if (nearbyAirportRadiusKm < 1 || nearbyAirportRadiusKm > 500) {
            throw new IllegalArgumentException("Nearby airport radius must be between 1 and 500 km");
        }

        if (nearbyAirportMaxResults < 1 || nearbyAirportMaxResults > 50) {
            throw new IllegalArgumentException("Nearby airport max results must be between 1 and 50");
        }
    }
}
