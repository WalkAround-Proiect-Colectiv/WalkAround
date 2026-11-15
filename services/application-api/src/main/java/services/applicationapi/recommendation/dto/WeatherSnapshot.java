package services.applicationapi.recommendation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record WeatherSnapshot(
        @Min(value = 0, message = "Weather code must be between 0 and 99")
        @Max(value = 99, message = "Weather code must be between 0 and 99")
        Integer weatherCode,

        @DecimalMin(value = "-90.0", message = "Temperature must be between -90 and 60 °C")
        @DecimalMax(value = "60.0", message = "Temperature must be between -90 and 60 °C")
        Double temperatureC,

        @DecimalMin(value = "-100.0", message = "Apparent temperature must be between -100 and 70 °C")
        @DecimalMax(value = "70.0", message = "Apparent temperature must be between -100 and 70 °C")
        Double apparentTemperatureC,

        @DecimalMin(value = "0.0", message = "Precipitation must be between 0 and 500 mm")
        @DecimalMax(value = "500.0", message = "Precipitation must be between 0 and 500 mm")
        Double precipitationMm,

        @Min(value = 0, message = "Precipitation probability must be between 0 and 100")
        @Max(value = 100, message = "Precipitation probability must be between 0 and 100")
        Integer precipitationProbability,

        @DecimalMin(value = "0.0", message = "Wind speed must be between 0 and 300 km/h")
        @DecimalMax(value = "300.0", message = "Wind speed must be between 0 and 300 km/h")
        Double windSpeedKmh,

        Boolean isDay
) {
}
