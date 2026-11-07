package services.applicationapi.recommendation.repository;

import java.util.List;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;

public interface ActivityRuleRepository {

    List<ActivityCategoryRule> findAllRules();
}
