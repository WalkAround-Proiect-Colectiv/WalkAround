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
import services.applicationapi.tripplan.dto.HistoricalWeatherResponse;
import services.applicationapi.tripplan.service.exceptions.HistoricalWeatherUnavailableException;

@Component
@ConditionalOnProperty(name = "app.trip-plan.weather-provider", havingValue = "open-meteo", matchIfMissing = true)
public class OpenMeteoHistoricalWeatherProvider implements HistoricalWeatherProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoHistoricalWeatherProvider.class);
    private static final String DAILY_VARIABLES = String.join(",",
            "weather_code", "temperature_2m_max", "temperature_2m_min",
            "precipitation_sum", "wind_speed_10m_max");

    private final RestClient restClient;

    public OpenMeteoHistoricalWeatherProvider(@Qualifier("openMeteoArchiveRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Period history(double latitude, double longitude, LocalDate startDate, LocalDate endDate) {
        ArchiveResponse response;
        try {
            response = restClient.get()
                    .uri(builder -> builder.path("/v1/archive")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("start_date", startDate)
                            .queryParam("end_date", endDate)
                            .queryParam("daily", DAILY_VARIABLES)
                            .queryParam("timezone", "auto")
                            .build())
                    .retrieve()
                    .body(ArchiveResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Open-Meteo archive returned HTTP {}", exception.getStatusCode().value());
            throw new HistoricalWeatherUnavailableException("Historical weather is temporarily unavailable", exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Open-Meteo archive request failed: {}", exception.getClass().getSimpleName());
            throw new HistoricalWeatherUnavailableException("Historical weather is temporarily unavailable", exception);
        }

        int expectedDays = Math.toIntExact(endDate.toEpochDay() - startDate.toEpochDay() + 1);
        if (response == null || response.timezone() == null || response.timezone().isBlank()
                || !validDaily(response.daily(), expectedDays)) {
            log.warn("Open-Meteo archive returned incomplete daily history");
            throw new HistoricalWeatherUnavailableException("Historical weather is temporarily unavailable");
        }

        Daily daily = response.daily();
        List<HistoricalWeatherResponse.Day> days = new ArrayList<>(expectedDays);
        try {
            for (int index = 0; index < expectedDays; index++) {
                LocalDate date = LocalDate.parse(daily.time().get(index));
                if (!date.equals(startDate.plusDays(index))) {
                    throw new HistoricalWeatherUnavailableException("Historical weather is temporarily unavailable");
                }
                days.add(new HistoricalWeatherResponse.Day(
                        date,
                        daily.weatherCode().get(index),
                        daily.temperatureMax().get(index),
                        daily.temperatureMin().get(index),
                        daily.precipitationSum().get(index),
                        daily.windSpeedMax().get(index)
                ));
            }
        } catch (DateTimeParseException | NullPointerException exception) {
            log.warn("Open-Meteo archive returned an invalid date");
            throw new HistoricalWeatherUnavailableException("Historical weather is temporarily unavailable", exception);
        }

        log.debug("Open-Meteo archive returned {} days for {} to {}", days.size(), startDate, endDate);
        return new Period(response.timezone(), List.copyOf(days));
    }

    private boolean validDaily(Daily daily, int expectedDays) {
        return daily != null && hasCount(daily.time(), expectedDays)
                && hasCount(daily.weatherCode(), expectedDays)
                && hasCount(daily.temperatureMax(), expectedDays)
                && hasCount(daily.temperatureMin(), expectedDays)
                && hasCount(daily.precipitationSum(), expectedDays)
                && hasCount(daily.windSpeedMax(), expectedDays);
    }

    private boolean hasCount(List<?> values, int expectedDays) {
        return values != null && values.size() == expectedDays;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArchiveResponse(String timezone, Daily daily) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum,
            @JsonProperty("wind_speed_10m_max") List<Double> windSpeedMax
    ) {
    }
}
