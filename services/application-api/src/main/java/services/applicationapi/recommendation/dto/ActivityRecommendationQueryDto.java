package services.applicationapi.recommendation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActivityRecommendationQueryDto(
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @Min(value = 0, message = "Radius must be between 0 and 2000 meters")
        @Max(value = 2000, message = "Radius must be between 0 and 2000 meters")
        Integer radiusMeters,

        @Min(value = 1, message = "Limit must be between 1 and 50")
        @Max(value = 50, message = "Limit must be between 1 and 50")
        Integer limit,

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
    public RecommendationRequest toRecommendationRequest() {
        boolean hasWeather = weatherCode != null
                || temperatureC != null
                || apparentTemperatureC != null
                || precipitationMm != null
                || precipitationProbability != null
                || windSpeedKmh != null
                || isDay != null;

        WeatherSnapshot snapshot = hasWeather
                ? new WeatherSnapshot(
                        weatherCode,
                        temperatureC,
                        apparentTemperatureC,
                        precipitationMm,
                        precipitationProbability,
                        windSpeedKmh,
                        isDay
                )
                : null;

        return new RecommendationRequest(latitude, longitude, radiusMeters, limit, snapshot);
    }
}
