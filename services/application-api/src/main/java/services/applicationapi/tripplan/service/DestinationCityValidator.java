package services.applicationapi.tripplan.service;

public interface DestinationCityValidator {

    ValidatedCity validate(String city, String countryCode);

    record ValidatedCity(String name, String countryName) {
    }
}
