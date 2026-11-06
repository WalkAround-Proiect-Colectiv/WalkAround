package services.applicationapi.recommendation.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import services.applicationapi.recommendation.config.RecommendationProperties;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;
import services.applicationapi.recommendation.domain.ActivityRecommendationResult;
import services.applicationapi.recommendation.domain.PoiCandidate;
import services.applicationapi.recommendation.domain.RankedActivityRecommendation;
import services.applicationapi.recommendation.domain.WeatherBucket;
import services.applicationapi.recommendation.domain.WeatherContext;
import services.applicationapi.recommendation.dto.RecommendationMapper;
import services.applicationapi.recommendation.dto.RecommendationRequest;
import services.applicationapi.recommendation.dto.RecommendationResponseDto;
import services.applicationapi.recommendation.dto.WeatherSnapshot;
import services.applicationapi.recommendation.exception.InvalidRecommendationRequestException;
import services.applicationapi.recommendation.service.WeatherProvider.WeatherFetchResult;

@Service
public class ActivityRecommendationServiceImpl implements ActivityRecommendationService {

    private final WeatherProvider weatherProvider;
    private final WeatherClassificationService weatherClassificationService;
    private final ActivityRuleService activityRuleService;
    private final PoiLookupService poiLookupService;
    private final RecommendationMapper mapper;
    private final RecommendationProperties properties;
    private final Clock clock;

    @Autowired
    public ActivityRecommendationServiceImpl(
            WeatherProvider weatherProvider,
            WeatherClassificationService weatherClassificationService,
            ActivityRuleService activityRuleService,
            PoiLookupService poiLookupService,
            RecommendationMapper mapper,
            RecommendationProperties properties
    ) {
        this(
                weatherProvider,
                weatherClassificationService,
                activityRuleService,
                poiLookupService,
                mapper,
                properties,
                Clock.systemUTC()
        );
    }

    public ActivityRecommendationServiceImpl(
            WeatherProvider weatherProvider,
            WeatherClassificationService weatherClassificationService,
            ActivityRuleService activityRuleService,
            PoiLookupService poiLookupService,
            RecommendationMapper mapper,
            RecommendationProperties properties,
            Clock clock
    ) {
        this.weatherProvider = weatherProvider;
        this.weatherClassificationService = weatherClassificationService;
        this.activityRuleService = activityRuleService;
        this.poiLookupService = poiLookupService;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RecommendationResponseDto getRecommendations(RecommendationRequest request) {
        validateRequest(request);

        int radiusMeters = resolveRadius(request.radiusMeters());
        int limit = properties.resolvedDefaultLimit();

        WeatherFetchResult weatherFetch = weatherProvider.fetchWeather(request.latitude(), request.longitude());
        WeatherSnapshot snapshot = weatherFetch != null ? weatherFetch.snapshot() : null;
        WeatherContext classified = weatherClassificationService.classify(snapshot);
        boolean isDegraded = classified.degraded() || (weatherFetch != null && weatherFetch.stale());
        WeatherContext context = new WeatherContext(
                classified.weatherBucket(),
                classified.temperatureBand(),
                classified.timeOfDay(),
                isDegraded
        );

        List<ActivityCategoryRule> rules = activityRuleService.getRulesForWeather(context);

        if (context.weatherBucket() == WeatherBucket.STORM) {
            rules = rules.stream()
                    .filter(ActivityCategoryRule::indoor)
                    .toList();
        }

        List<PoiCandidate> candidates = poiLookupService.findNearbyPois(
                request.latitude(),
                request.longitude(),
                radiusMeters,
                limit,
                rules
        );

        List<RankedActivityRecommendation> ranked = new ArrayList<>();
        for (PoiCandidate candidate : candidates) {
            ActivityCategoryRule matchedRule = findBestMatchingRule(candidate, rules);
            if (matchedRule == null) {
                continue;
            }
            if (context.weatherBucket() == WeatherBucket.STORM && !matchedRule.indoor()) {
                continue;
            }

            double score = calculateScore(matchedRule.weight(), candidate.distanceMeters(), radiusMeters);
            int roundedDistance = (int) Math.round(candidate.distanceMeters());
            String reason = buildReason(context, matchedRule);

            ranked.add(new RankedActivityRecommendation(
                    candidate.id(),
                    candidate.name(),
                    matchedRule.categoryCode(),
                    matchedRule.displayName(),
                    matchedRule.indoor(),
                    candidate.latitude(),
                    candidate.longitude(),
                    roundedDistance,
                    score,
                    reason,
                    null,
                    null
            ));
        }

        ranked.sort(Comparator
                .comparingDouble(RankedActivityRecommendation::score).reversed()
                .thenComparingInt(RankedActivityRecommendation::distanceMeters)
                .thenComparing(RankedActivityRecommendation::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RankedActivityRecommendation::id));

        List<RankedActivityRecommendation> topRecommendations = ranked.size() > limit
                ? ranked.subList(0, limit)
                : ranked;

        Double temperatureC = snapshot != null ? snapshot.temperatureC() : null;
        String summary = buildWeatherSummary(context, snapshot);
        ActivityRecommendationResult result = new ActivityRecommendationResult(
                context.weatherBucket(),
                temperatureC,
                summary,
                context.degraded(),
                topRecommendations,
                Instant.now(clock)
        );

        return mapper.toResponseDto(result);
    }

    /**
     * Deterministic ranking score combining category weight (primary) with normalized distance proximity (up to 30%).
     *
     * <pre>
     * score = weight * (1.0 - 0.3 * clamp(distanceMeters / radiusMeters, 0.0, 1.0))
     * </pre>
     */
    public static double calculateScore(int weight, double distanceMeters, int radiusMeters) {
        double normalizedDistance = Math.min(1.0, Math.max(0.0, distanceMeters / Math.max(1.0, radiusMeters)));
        return weight * (1.0 - 0.3 * normalizedDistance);
    }

    private ActivityCategoryRule findBestMatchingRule(PoiCandidate candidate, List<ActivityCategoryRule> rules) {
        for (ActivityCategoryRule rule : rules) {
            if (PoiLookupServiceImpl.matchesOsmFilter(rule.osmFilter(), candidate.osmKind(), candidate.osmClass())) {
                return rule;
            }
        }
        return null;
    }

    private String buildReason(WeatherContext context, ActivityCategoryRule rule) {
        String label = rule.displayName().toLowerCase(Locale.ROOT);
        if (context.degraded() && context.weatherBucket() == WeatherBucket.CLOUDY) {
            return "Weather data is unavailable, so here is a popular nearby spot for " + label + ".";
        }
        return switch (context.weatherBucket()) {
            case CLEAR -> switch (context.temperatureBand()) {
                case HOT -> rule.indoor()
                        ? "Warm and sunny outside, making indoor " + label + " a cool break."
                        : "Clear and warm weather is ideal for " + label + ".";
                case COLD -> rule.indoor()
                        ? "Crisp, cold skies outside make cozy " + label + " a great stop."
                        : "Clear and crisp weather is great for visiting " + label + ".";
                case MILD -> "Clear and mild right now, perfect for enjoying " + label + ".";
            };
            case CLOUDY -> rule.indoor()
                    ? "Overcast skies make indoor " + label + " a comfortable choice."
                    : "Mild cloudy conditions are pleasant for exploring " + label + ".";
            case RAIN -> "Rainy right now, so indoor " + label + " keeps you dry.";
            case STORM -> "Thunderstorm conditions nearby — indoor " + label + " keeps you safe and sheltered.";
            case SNOW -> "Snowy weather outside makes warm indoor " + label + " a cozy pick.";
            case FOG -> "Foggy conditions outside — indoor " + label + " is a comfortable option.";
        };
    }

    private String buildWeatherSummary(WeatherContext context, WeatherSnapshot snapshot) {
        if (snapshot == null) {
            return "Weather unavailable · showing general nearby recommendations";
        }
        String tempPart = snapshot.temperatureC() != null
                ? String.format(Locale.ROOT, " (%.0f°C)", snapshot.temperatureC())
                : "";
        return switch (context.weatherBucket()) {
            case CLEAR -> switch (context.temperatureBand()) {
                case HOT -> "Clear and warm" + tempPart;
                case COLD -> "Clear and crisp" + tempPart;
                case MILD -> "Clear and mild" + tempPart;
            };
            case CLOUDY -> "Cloudy skies" + tempPart;
            case RAIN -> "Rain or drizzle" + tempPart;
            case STORM -> "Thunderstorm alert" + tempPart;
            case SNOW -> "Snowy conditions" + tempPart;
            case FOG -> "Foggy conditions" + tempPart;
        };
    }

    private void validateRequest(RecommendationRequest request) {
        if (request == null || request.latitude() == null || request.longitude() == null) {
            throw new InvalidRecommendationRequestException("Latitude and longitude are required");
        }
        if (!Double.isFinite(request.latitude()) || request.latitude() < -90.0 || request.latitude() > 90.0) {
            throw new InvalidRecommendationRequestException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(request.longitude()) || request.longitude() < -180.0 || request.longitude() > 180.0) {
            throw new InvalidRecommendationRequestException("Longitude must be between -180 and 180");
        }
        if (request.radiusMeters() != null
                && (request.radiusMeters() < 100 || request.radiusMeters() > properties.resolvedMaxRadiusMeters())) {
            throw new InvalidRecommendationRequestException(
                    "Radius must be between 100 and " + properties.resolvedMaxRadiusMeters() + " meters");
        }
    }

    private int resolveRadius(Integer requestedRadius) {
        if (requestedRadius == null) {
            return properties.resolvedDefaultRadiusMeters();
        }
        return Math.min(properties.resolvedMaxRadiusMeters(), Math.max(100, requestedRadius));
    }
}
