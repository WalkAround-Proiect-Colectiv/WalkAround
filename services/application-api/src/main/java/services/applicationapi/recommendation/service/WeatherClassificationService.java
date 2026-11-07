package services.applicationapi.recommendation.service;

import services.applicationapi.recommendation.domain.WeatherContext;
import services.applicationapi.recommendation.dto.WeatherSnapshot;

public interface WeatherClassificationService {

    WeatherContext classify(WeatherSnapshot snapshot);
}
