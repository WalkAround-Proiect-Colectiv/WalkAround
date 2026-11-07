package services.applicationapi.recommendation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import services.applicationapi.recommendation.config.RecommendationProperties;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;
import services.applicationapi.recommendation.domain.TemperatureBand;
import services.applicationapi.recommendation.domain.WeatherBucket;
import services.applicationapi.recommendation.domain.WeatherContext;
import services.applicationapi.recommendation.repository.ActivityRuleRepository;

@Service
public class ActivityRuleServiceImpl implements ActivityRuleService {

    private static final List<ActivityCategoryRule> DEFAULT_FALLBACK_RULES = List.of(
            new ActivityCategoryRule("PARK", "Parks & Gardens", "trees", false, 90, "landcover:park,garden", WeatherBucket.CLOUDY, null, null),
            new ActivityCategoryRule("MUSEUM", "Museums", "landmark", true, 85, "poi:museum", WeatherBucket.CLOUDY, null, null),
            new ActivityCategoryRule("CAFE", "Cafés", "coffee", true, 80, "poi:cafe", WeatherBucket.CLOUDY, null, null),
            new ActivityCategoryRule("GALLERY", "Art Galleries", "palette", true, 75, "poi:gallery", WeatherBucket.CLOUDY, null, null)
    );

    private static final List<ActivityCategoryRule> INDOOR_FALLBACK_RULES = List.of(
            new ActivityCategoryRule("MUSEUM", "Museums", "landmark", true, 100, "poi:museum", WeatherBucket.STORM, null, null),
            new ActivityCategoryRule("CINEMA", "Cinemas", "film", true, 95, "poi:cinema", WeatherBucket.STORM, null, null),
            new ActivityCategoryRule("LIBRARY", "Libraries", "book-open", true, 90, "poi:library", WeatherBucket.STORM, null, null)
    );

    private final ActivityRuleRepository ruleRepository;
    private final RecommendationProperties properties;
    private final Clock clock;

    private volatile List<ActivityCategoryRule> cachedRules;
    private volatile Instant cacheExpiresAt = Instant.MIN;

    @Autowired
    public ActivityRuleServiceImpl(
            ActivityRuleRepository ruleRepository,
            RecommendationProperties properties
    ) {
        this(ruleRepository, properties, Clock.systemUTC());
    }

    public ActivityRuleServiceImpl(
            ActivityRuleRepository ruleRepository,
            RecommendationProperties properties,
            Clock clock
    ) {
        this.ruleRepository = ruleRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public List<ActivityCategoryRule> getRulesForWeather(WeatherContext context) {
        WeatherBucket bucket = context != null && context.weatherBucket() != null
                ? context.weatherBucket()
                : WeatherBucket.CLOUDY;
        TemperatureBand temperatureBand = context != null ? context.temperatureBand() : TemperatureBand.MILD;

        List<ActivityCategoryRule> allRules = loadCachedRules();

        List<ActivityCategoryRule> matched = allRules.stream()
                .filter(rule -> rule.weatherBucket() == bucket)
                .filter(rule -> rule.temperatureBand() == null || rule.temperatureBand() == temperatureBand)
                .filter(rule -> context == null || context.timeOfDay() == null
                        || rule.timeOfDay() == null || rule.timeOfDay() == context.timeOfDay())
                .sorted(Comparator
                        .comparingInt(ActivityCategoryRule::weight).reversed()
                        .thenComparing(rule -> rule.temperatureBand() != null ? 0 : 1)
                        .thenComparing(ActivityCategoryRule::categoryCode))
                .toList();

        if (matched.isEmpty()) {
            return bucket == WeatherBucket.STORM ? INDOOR_FALLBACK_RULES : DEFAULT_FALLBACK_RULES;
        }

        Map<String, ActivityCategoryRule> deduplicated = new LinkedHashMap<>();
        for (ActivityCategoryRule rule : matched) {
            deduplicated.putIfAbsent(rule.categoryCode(), rule);
        }

        return List.copyOf(deduplicated.values());
    }

    private List<ActivityCategoryRule> loadCachedRules() {
        Instant now = Instant.now(clock);
        List<ActivityCategoryRule> current = this.cachedRules;
        if (current != null && now.isBefore(this.cacheExpiresAt)) {
            return current;
        }
        synchronized (this) {
            if (this.cachedRules != null && now.isBefore(this.cacheExpiresAt)) {
                return this.cachedRules;
            }
            List<ActivityCategoryRule> fresh = ruleRepository.findAllRules();
            this.cachedRules = fresh != null ? List.copyOf(fresh) : List.of();
            this.cacheExpiresAt = now.plus(properties.resolvedCacheTtl());
            return this.cachedRules;
        }
    }
}
