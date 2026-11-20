package services.applicationapi.tripplan.service.bussearch;

import java.util.List;
import services.applicationapi.tripplan.dto.BusItineraryResponse;
import services.applicationapi.tripplan.dto.BusSearchRequest;

public interface BusSearchService {

    List<BusItineraryResponse> search(BusSearchRequest request);
}
