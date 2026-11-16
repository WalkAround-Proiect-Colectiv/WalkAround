package services.applicationapi.tripplan.service.trainsearch;

import java.util.List;
import services.applicationapi.tripplan.dto.TrainItineraryResponse;
import services.applicationapi.tripplan.dto.TrainSearchRequest;

public interface BiloTrainSearchService {

    boolean enabled();

    List<TrainItineraryResponse> search(TrainSearchRequest request, String origin, String destination);
}
