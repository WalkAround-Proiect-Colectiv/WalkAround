package services.applicationapi.tripplan.service.exceptions;

public class InvalidHistoricalWeatherRequestException extends RuntimeException {

    public InvalidHistoricalWeatherRequestException(String message) {
        super(message);
    }
}
