package services.applicationapi.recommendation.service;

import services.applicationapi.recommendation.dto.WeatherSnapshot;

public interface WeatherProvider {

    WeatherFetchResult fetchWeather(double latitude, double longitude);

    record WeatherFetchResult(WeatherSnapshot snapshot, boolean stale) {
        public static WeatherFetchResult empty() {
            return new WeatherFetchResult(null, true);
        }
    }
}
