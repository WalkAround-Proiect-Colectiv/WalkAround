package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import services.applicationapi.tripplan.model.TripPlan;

public interface TripPlanRepository extends Repository<TripPlan, UUID> {

    TripPlan save(TripPlan tripPlan);

    Optional<TripPlan> findByIdAndUser_Id(UUID id, UUID userId);
}
