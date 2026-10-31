package services.applicationapi.tripplan.service;

import java.util.List;
import services.applicationapi.tripplan.dto.FlightItineraryResponse;
import services.applicationapi.tripplan.dto.FlightSearchRequest;

public interface FlightSearchService {

    List<FlightItineraryResponse> search(FlightSearchRequest request);
}
