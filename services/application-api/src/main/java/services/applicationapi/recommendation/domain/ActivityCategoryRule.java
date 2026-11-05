package services.applicationapi.recommendation.domain;

public record ActivityCategoryRule(
        String categoryCode,
        String displayName,
        String iconKey,
        boolean indoor,
        int weight,
        String osmFilter,
        WeatherBucket weatherBucket,
        TemperatureBand temperatureBand,
        TimeOfDay timeOfDay
) {
}
