package services.applicationapi.tripplan.service.accommodationsearch;

public interface AccommodationCityValidator {

    ValidatedCity validate(String city, String countryCode);

    record ValidatedCity(String name, String countryName) {
    }
}
