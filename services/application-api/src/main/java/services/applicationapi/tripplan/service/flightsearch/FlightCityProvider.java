package services.applicationapi.tripplan.service.flightsearch;

public interface FlightCityProvider {

    String findCityId(String city, String countryName);
}
