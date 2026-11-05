package services.applicationapi.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RecommendationRequest(
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

        @Valid
        WeatherSnapshot weather
) {
}
