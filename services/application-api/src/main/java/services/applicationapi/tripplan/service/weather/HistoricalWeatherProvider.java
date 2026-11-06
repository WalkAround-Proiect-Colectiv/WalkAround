package services.applicationapi.tripplan.service.weather;

import java.time.LocalDate;
import java.util.List;
import services.applicationapi.tripplan.dto.HistoricalWeatherResponse;

public interface HistoricalWeatherProvider {

    Period history(double latitude, double longitude, LocalDate startDate, LocalDate endDate);

    record Period(String timezone, List<HistoricalWeatherResponse.Day> days) {
    }
}
