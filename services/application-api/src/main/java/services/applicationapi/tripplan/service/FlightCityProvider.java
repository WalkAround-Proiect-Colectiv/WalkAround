package services.applicationapi.tripplan.service;

public interface FlightCityProvider {

    String findCityId(String city, String countryName);
}
