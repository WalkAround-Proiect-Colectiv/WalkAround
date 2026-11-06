package services.applicationapi.tripplan.dto;

import java.time.LocalDate;
import java.util.List;

public record HistoricalWeatherResponse(
        String city,
        String country,
        String countryCode,
        String timezone,
        LocalDate checkInDate,
        int stayingNights,
        List<Year> years
) {
    public record Year(int year, LocalDate startDate, LocalDate endDate, List<Day> days) {
    }

    public record Day(
            LocalDate date,
            Integer weatherCode,
            Double temperatureMaxC,
            Double temperatureMinC,
            Double precipitationMm,
            Double windSpeedMaxKmh
    ) {
    }
}
