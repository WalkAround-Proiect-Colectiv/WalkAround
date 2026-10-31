package services.applicationapi.tripplan.service;

import java.util.List;
import services.applicationapi.tripplan.dto.AirportResponse;

public interface CityAirportService {

    List<AirportResponse> findAirports(String city, String countryCode);
}
