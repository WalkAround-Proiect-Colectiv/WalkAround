package services.applicationapi.tripplan.service.trainsearch;

import java.util.List;
import services.applicationapi.tripplan.dto.TrainItineraryResponse;
import services.applicationapi.tripplan.dto.TrainSearchRequest;

public interface TrainSearchService {

    List<TrainItineraryResponse> search(TrainSearchRequest request);
}
