package services.applicationapi.tripplan.service.weather;

import services.applicationapi.tripplan.dto.DestinationWeatherResponse;

public interface WeatherForecastProvider {

    DestinationWeatherResponse forecast(Location location);

    record Location(String city, String country, String countryCode, double latitude, double longitude) {
    }
}
