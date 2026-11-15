package services.applicationapi.recommendation.service;

import java.util.List;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;
import services.applicationapi.recommendation.domain.WeatherContext;

public interface ActivityRuleService {

    List<ActivityCategoryRule> getRulesForWeather(WeatherContext context);
}
