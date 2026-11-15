package services.applicationapi.tripplan.service.cityairport;

public interface CityLocationProvider {

    CityLocation find(String city, String countryCode);

    record CityLocation(double latitude, double longitude) {
    }
}
