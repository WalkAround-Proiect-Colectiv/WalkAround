package services.applicationapi.tripplan.dto;

import java.time.LocalDate;
import java.util.List;

public record DestinationWeatherResponse(
        String city,
        String country,
        String countryCode,
        String timezone,
        List<Day> days
) {
    public record Day(
            LocalDate date,
            Integer weatherCode,
            Double temperatureMaxC,
            Double temperatureMinC,
            Double precipitationProbabilityPercent,
            Double precipitationMm,
            Double windSpeedMaxKmh
    ) {
    }
}
