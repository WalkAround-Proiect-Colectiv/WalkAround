package services.applicationapi.tripplan.service.exceptions;

public class CitySearchUnavailableException extends RuntimeException {

    public CitySearchUnavailableException(String message) {
        super(message);
    }

    public CitySearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
