package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import services.applicationapi.tripplan.model.TripPlanGroup;

public interface TripPlanGroupRepository extends Repository<TripPlanGroup, UUID> {

    TripPlanGroup save(TripPlanGroup group);

    Optional<TripPlanGroup> findById(UUID id);

}
