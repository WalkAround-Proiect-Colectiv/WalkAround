package services.applicationapi.tripplan.service.weather;

import services.applicationapi.tripplan.dto.DestinationWeatherResponse;

public interface WeatherForecastService {

    DestinationWeatherResponse get(String city, String countryCode);
}
