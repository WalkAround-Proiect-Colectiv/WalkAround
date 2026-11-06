package services.applicationapi.tripplan.service.exceptions;

public class WeatherForecastUnavailableException extends RuntimeException {

    public WeatherForecastUnavailableException(String message) {
        super(message);
    }

    public WeatherForecastUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
