package services.applicationapi.recommendation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.recommendation.config.RecommendationProperties;
import services.applicationapi.recommendation.domain.WeatherBucket;
import services.applicationapi.recommendation.dto.WeatherSnapshot;

@Component
public class OpenMeteoWeatherProviderImpl implements WeatherProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherProviderImpl.class);
    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,is_day";
    private static final String HOURLY_FIELDS = "precipitation_probability,weather_code";
    private static final int FORECAST_HOURS = 3;
    private static final int HIGH_PRECIPITATION_PROBABILITY_THRESHOLD = 60;
    private static final int DEFAULT_RAIN_WMO_CODE = 61;

    private final RestClient restClient;
    private final RecommendationProperties properties;
    private final Clock clock;
    private final Map<String, CachedWeather> cache = new ConcurrentHashMap<>();

    @Autowired
    public OpenMeteoWeatherProviderImpl(
            @Qualifier("openMeteoRestClient") RestClient restClient,
            RecommendationProperties properties
    ) {
        this(restClient, properties, Clock.systemUTC());
    }

    public OpenMeteoWeatherProviderImpl(
            RestClient restClient,
            RecommendationProperties properties,
            Clock clock
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WeatherFetchResult fetchWeather(double latitude, double longitude) {
        String cacheKey = buildCacheKey(latitude, longitude);
        Instant now = Instant.now(clock);

        CachedWeather existing = cache.get(cacheKey);
        if (existing != null && now.isBefore(existing.expiresAt())) {
            return new WeatherFetchResult(existing.snapshot(), false);
        }

        WeatherSnapshot fetched = executeWithSingleRetry(latitude, longitude);
        if (fetched != null) {
            cache.put(cacheKey, new CachedWeather(fetched, now.plus(properties.resolvedCacheTtl())));
            return new WeatherFetchResult(fetched, false);
        }

        if (existing != null) {
            log.debug("Using stale cached weather snapshot after Open-Meteo failure");
            return new WeatherFetchResult(existing.snapshot(), true);
        }

        return WeatherFetchResult.empty();
    }

    private WeatherSnapshot executeWithSingleRetry(double latitude, double longitude) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                OpenMeteoResponse response = restClient.get()
                        .uri(builder -> builder.path("/v1/forecast")
                                .queryParam("latitude", String.format(Locale.ROOT, "%.4f", latitude))
                                .queryParam("longitude", String.format(Locale.ROOT, "%.4f", longitude))
                                .queryParam("current", CURRENT_FIELDS)
                                .queryParam("hourly", HOURLY_FIELDS)
                                .queryParam("forecast_hours", FORECAST_HOURS)
                                .queryParam("timezone", "auto")
                                .build())
                        .retrieve()
                        .body(OpenMeteoResponse.class);

                return parseAndValidateResponse(response);
            } catch (RestClientResponseException httpEx) {
                if (httpEx.getStatusCode().is4xxClientError()) {
                    log.warn("Open-Meteo returned client error status {}", httpEx.getStatusCode().value());
                    return null;
                }
                log.warn("Open-Meteo returned server error status {} (attempt {})",
                        httpEx.getStatusCode().value(), attempt + 1);
            } catch (RestClientException | IllegalArgumentException ex) {
                log.warn("Open-Meteo request failed with {} (attempt {})",
                        ex.getClass().getSimpleName(), attempt + 1);
            }
        }
        return null;
    }

    public static WeatherSnapshot parseAndValidateResponse(OpenMeteoResponse response) {
        if (response == null || response.current() == null) {
            return null;
        }
        CurrentBlock current = response.current();
        if (current.weatherCode() == null || current.temperature2m() == null) {
            return null;
        }
        if (current.weatherCode() < 0 || current.weatherCode() > 99) {
            return null;
        }
        if (!Double.isFinite(current.temperature2m())
                || current.temperature2m() < -90.0
                || current.temperature2m() > 60.0) {
            return null;
        }

        List<Integer> hourlyCodes = response.hourly() != null ? response.hourly().weatherCode() : null;
        List<Integer> hourlyPrecipProbs = response.hourly() != null
                ? response.hourly().precipitationProbability()
                : null;

        int effectiveWeatherCode = resolveImmediateFutureWeatherCode(
                current.weatherCode(),
                hourlyCodes,
                hourlyPrecipProbs
        );
        Integer maxPrecipProb = resolveMaxPrecipitationProbability(hourlyPrecipProbs);
        Boolean isDay = current.isDay() != null ? current.isDay() == 1 : null;

        return new WeatherSnapshot(
                effectiveWeatherCode,
                current.temperature2m(),
                current.apparentTemperature(),
                current.precipitation(),
                maxPrecipProb,
                current.windSpeed10m(),
                isDay
        );
    }

    /**
     * Immediate-future lookahead rule: if the next 1-3 hours are materially worse than the current
     * weather code (e.g. storm/snow/rain approaching or precipitation probability >= 60%), returns
     * the worse weather code so recommendations adapt ahead of time.
     */
    public static int resolveImmediateFutureWeatherCode(
            int currentCode,
            List<Integer> hourlyCodes,
            List<Integer> hourlyPrecipProbabilities
    ) {
        int worstCode = currentCode;
        int worstSeverity = bucketSeverity(WeatherClassificationServiceImpl.mapWmoCode(currentCode));

        if (hourlyCodes != null) {
            int count = Math.min(FORECAST_HOURS, hourlyCodes.size());
            for (int i = 0; i < count; i++) {
                Integer code = hourlyCodes.get(i);
                if (code != null && code >= 0 && code <= 99) {
                    int severity = bucketSeverity(WeatherClassificationServiceImpl.mapWmoCode(code));
                    if (severity > worstSeverity) {
                        worstSeverity = severity;
                        worstCode = code;
                    }
                }
            }
        }

        Integer maxProb = resolveMaxPrecipitationProbability(hourlyPrecipProbabilities);
        if (maxProb != null
                && maxProb >= HIGH_PRECIPITATION_PROBABILITY_THRESHOLD
                && worstSeverity < bucketSeverity(WeatherBucket.RAIN)) {
            worstCode = DEFAULT_RAIN_WMO_CODE;
        }

        return worstCode;
    }

    public static Integer resolveMaxPrecipitationProbability(List<Integer> probabilities) {
        if (probabilities == null || probabilities.isEmpty()) {
            return null;
        }
        Integer max = null;
        int count = Math.min(FORECAST_HOURS, probabilities.size());
        for (int i = 0; i < count; i++) {
            Integer val = probabilities.get(i);
            if (val != null && val >= 0 && val <= 100) {
                max = max == null ? val : Math.max(max, val);
            }
        }
        return max;
    }

    public static String buildCacheKey(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.2f:%.2f", latitude, longitude);
    }

    private static int bucketSeverity(WeatherBucket bucket) {
        return switch (bucket) {
            case CLEAR -> 0;
            case CLOUDY -> 1;
            case FOG -> 2;
            case RAIN -> 3;
            case SNOW -> 4;
            case STORM -> 5;
        };
    }

    private record CachedWeather(WeatherSnapshot snapshot, Instant expiresAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenMeteoResponse(
            @JsonProperty("current") CurrentBlock current,
            @JsonProperty("hourly") HourlyBlock hourly
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentBlock(
            @JsonProperty("temperature_2m") Double temperature2m,
            @JsonProperty("apparent_temperature") Double apparentTemperature,
            @JsonProperty("precipitation") Double precipitation,
            @JsonProperty("weather_code") Integer weatherCode,
            @JsonProperty("wind_speed_10m") Double windSpeed10m,
            @JsonProperty("is_day") Integer isDay
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HourlyBlock(
            @JsonProperty("precipitation_probability") List<Integer> precipitationProbability,
            @JsonProperty("weather_code") List<Integer> weatherCode
    ) {
    }
}
