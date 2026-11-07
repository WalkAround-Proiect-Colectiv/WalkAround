package services.applicationapi.recommendation.domain;

public record WeatherContext(
        WeatherBucket weatherBucket,
        TemperatureBand temperatureBand,
        TimeOfDay timeOfDay,
        boolean degraded
) {
}
