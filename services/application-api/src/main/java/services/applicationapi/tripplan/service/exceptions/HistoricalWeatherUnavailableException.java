package services.applicationapi.tripplan.service.exceptions;

public class HistoricalWeatherUnavailableException extends RuntimeException {

    public HistoricalWeatherUnavailableException(String message) {
        super(message);
    }

    public HistoricalWeatherUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
