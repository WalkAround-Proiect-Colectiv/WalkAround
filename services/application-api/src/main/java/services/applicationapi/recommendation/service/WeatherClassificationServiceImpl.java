package services.applicationapi.recommendation.service;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import services.applicationapi.recommendation.domain.TemperatureBand;
import services.applicationapi.recommendation.domain.TimeOfDay;
import services.applicationapi.recommendation.domain.WeatherBucket;
import services.applicationapi.recommendation.domain.WeatherContext;
import services.applicationapi.recommendation.dto.WeatherSnapshot;

@Service
public class WeatherClassificationServiceImpl implements WeatherClassificationService {

    public static final double COLD_UPPER_EXCLUSIVE_C = 10.0;
    public static final double HOT_LOWER_EXCLUSIVE_C = 26.0;

    private final Clock clock;

    public WeatherClassificationServiceImpl() {
        this(Clock.systemUTC());
    }

    public WeatherClassificationServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public WeatherContext classify(WeatherSnapshot snapshot) {
        if (snapshot == null || snapshot.weatherCode() == null) {
            return new WeatherContext(
                    WeatherBucket.CLOUDY,
                    TemperatureBand.MILD,
                    resolveTimeOfDay(snapshot),
                    true
            );
        }

        Double effectiveTemp = snapshot.apparentTemperatureC() != null
                ? snapshot.apparentTemperatureC()
                : snapshot.temperatureC();

        if (effectiveTemp == null || !Double.isFinite(effectiveTemp)) {
            return new WeatherContext(
                    mapWmoCode(snapshot.weatherCode()),
                    TemperatureBand.MILD,
                    resolveTimeOfDay(snapshot),
                    true
            );
        }

        WeatherBucket bucket = mapWmoCode(snapshot.weatherCode());
        TemperatureBand temperatureBand = mapTemperatureBand(effectiveTemp);
        TimeOfDay timeOfDay = resolveTimeOfDay(snapshot);

        return new WeatherContext(bucket, temperatureBand, timeOfDay, false);
    }

    public static WeatherBucket mapWmoCode(int code) {
        if (code == 0) {
            return WeatherBucket.CLEAR;
        }
        if (code >= 1 && code <= 3) {
            return WeatherBucket.CLOUDY;
        }
        if (code == 45 || code == 48) {
            return WeatherBucket.FOG;
        }
        if ((code >= 51 && code <= 57) || (code >= 61 && code <= 67) || (code >= 80 && code <= 82)) {
            return WeatherBucket.RAIN;
        }
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            return WeatherBucket.SNOW;
        }
        if (code >= 95 && code <= 99) {
            return WeatherBucket.STORM;
        }
        return WeatherBucket.CLOUDY;
    }

    public static TemperatureBand mapTemperatureBand(double temperatureC) {
        if (temperatureC < COLD_UPPER_EXCLUSIVE_C) {
            return TemperatureBand.COLD;
        }
        if (temperatureC > HOT_LOWER_EXCLUSIVE_C) {
            return TemperatureBand.HOT;
        }
        return TemperatureBand.MILD;
    }

    private TimeOfDay resolveTimeOfDay(WeatherSnapshot snapshot) {
        if (snapshot != null && snapshot.isDay() != null) {
            return snapshot.isDay() ? TimeOfDay.DAY : TimeOfDay.NIGHT;
        }
        LocalTime now = LocalTime.now(clock.withZone(ZoneOffset.UTC));
        return (now.getHour() >= 6 && now.getHour() < 20) ? TimeOfDay.DAY : TimeOfDay.NIGHT;
    }
}
