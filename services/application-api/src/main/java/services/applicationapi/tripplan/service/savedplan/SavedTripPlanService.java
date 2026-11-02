package services.applicationapi.tripplan.service.savedplan;

import java.util.UUID;
import services.applicationapi.tripplan.dto.SaveTripPlanRequest;
import services.applicationapi.tripplan.dto.TripPlanResponse;

public interface SavedTripPlanService {

    TripPlanResponse create(UUID userId, SaveTripPlanRequest request);

    TripPlanResponse get(UUID userId, UUID planId);
}
