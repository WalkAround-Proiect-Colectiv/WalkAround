package services.applicationapi.tripplan.service.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.tripplan.configurations.WeatherForecastProperties;
import services.applicationapi.tripplan.dto.DestinationWeatherResponse;
import services.applicationapi.tripplan.service.exceptions.WeatherForecastUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.weather-provider", havingValue = "open-meteo", matchIfMissing = true)
public class OpenMeteoWeatherForecastProvider implements WeatherForecastProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoWeatherForecastProvider.class);
    private static final String DAILY_VARIABLES = String.join(",",
            "weather_code", "temperature_2m_max", "temperature_2m_min",
            "precipitation_probability_max", "precipitation_sum", "wind_speed_10m_max");

    private final RestClient restClient;
    private final WeatherForecastProperties properties;

    public OpenMeteoWeatherForecastProvider(
            @Qualifier("openMeteoRestClient") RestClient restClient,
            WeatherForecastProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public DestinationWeatherResponse forecast(Location location) {
        ForecastResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/v1/forecast")
                            .queryParam("latitude", location.latitude())
                            .queryParam("longitude", location.longitude())
                            .queryParam("daily", DAILY_VARIABLES)
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", properties.weatherForecastDays())
                            .build())
                    .retrieve()
                    .body(ForecastResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Open-Meteo returned HTTP {}", exception.getStatusCode().value());
            throw new WeatherForecastUnavailableException("Weather is temporarily unavailable", exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Open-Meteo request failed: {}", exception.getClass().getSimpleName());
            throw new WeatherForecastUnavailableException("Weather is temporarily unavailable", exception);
        }

        if (response == null || response.timezone() == null || response.timezone().isBlank()
                || !validDaily(response.daily())) {
            log.warn("Open-Meteo returned an invalid daily forecast");
            throw new WeatherForecastUnavailableException("Weather is temporarily unavailable");
        }

        Daily daily = response.daily();
        List<DestinationWeatherResponse.Day> days = new ArrayList<>(daily.time().size());
        try {
            for (int index = 0; index < daily.time().size(); index++) {
                days.add(new DestinationWeatherResponse.Day(
                        LocalDate.parse(daily.time().get(index)),
                        daily.weatherCode().get(index),
                        daily.temperatureMax().get(index),
                        daily.temperatureMin().get(index),
                        daily.precipitationProbability().get(index),
                        daily.precipitationSum().get(index),
                        daily.windSpeedMax().get(index)
                ));
            }
        } catch (DateTimeParseException | NullPointerException exception) {
            log.warn("Open-Meteo returned an invalid forecast date");
            throw new WeatherForecastUnavailableException("Weather is temporarily unavailable", exception);
        }

        log.debug("Open-Meteo returned {} forecast days for {}", days.size(), location.countryCode());
        return new DestinationWeatherResponse(
                location.city(), location.country(), location.countryCode(), response.timezone(), List.copyOf(days));
    }

    private boolean validDaily(Daily daily) {
        if (daily == null || daily.time() == null || daily.time().isEmpty()
                || daily.time().size() > properties.weatherForecastDays()) {
            return false;
        }

        int count = daily.time().size();
        return hasCount(daily.weatherCode(), count)
                && hasCount(daily.temperatureMax(), count)
                && hasCount(daily.temperatureMin(), count)
                && hasCount(daily.precipitationProbability(), count)
                && hasCount(daily.precipitationSum(), count)
                && hasCount(daily.windSpeedMax(), count);
    }

    private boolean hasCount(List<?> values, int count) {
        return values != null && values.size() == count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ForecastResponse(String timezone, Daily daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
            @JsonProperty("precipitation_probability_max") List<Double> precipitationProbability,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum,
            @JsonProperty("wind_speed_10m_max") List<Double> windSpeedMax
    ) {
    }
}
