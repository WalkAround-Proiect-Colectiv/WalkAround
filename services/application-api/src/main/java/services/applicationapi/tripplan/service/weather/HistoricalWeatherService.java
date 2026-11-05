package services.applicationapi.tripplan.service.weather;

import services.applicationapi.tripplan.dto.HistoricalWeatherResponse;

public interface HistoricalWeatherService {

    HistoricalWeatherResponse get(String city, String countryCode, String checkInDate, String stayingNights);
}
