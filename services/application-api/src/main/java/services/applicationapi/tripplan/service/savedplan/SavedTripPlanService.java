package services.applicationapi.tripplan.service.savedplan;

import java.util.UUID;
import java.util.List;
import services.applicationapi.tripplan.dto.SaveTripPlanRequest;
import services.applicationapi.tripplan.dto.TripPlanResponse;

public interface SavedTripPlanService {

    record PersonalPage(List<TripPlanResponse> items, Integer nextPage) {}

    PersonalPage listPersonal(UUID userId, int page);

    TripPlanResponse create(UUID userId, SaveTripPlanRequest request);

    TripPlanResponse get(UUID userId, UUID planId);

    TripPlanResponse createForGroup(UUID groupId, UUID userId, SaveTripPlanRequest request);

    TripPlanResponse getForGroup(UUID groupId, UUID userId, UUID planId);

    List<TripPlanResponse> listForGroup(UUID groupId, UUID userId);

    TripPlanResponse makeCurrent(UUID groupId, UUID adminId, UUID planId);
}
