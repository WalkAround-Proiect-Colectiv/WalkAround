package services.applicationapi.tripplan.service.exceptions;

public class InvalidWeatherForecastRequestException extends RuntimeException {

    public InvalidWeatherForecastRequestException(String message) {
        super(message);
    }
}
