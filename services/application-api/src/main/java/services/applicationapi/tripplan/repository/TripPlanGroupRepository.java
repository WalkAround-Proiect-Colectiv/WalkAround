package services.applicationapi.tripplan.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import services.applicationapi.tripplan.model.TripPlanGroup;

public interface TripPlanGroupRepository extends Repository<TripPlanGroup, UUID> {

    TripPlanGroup save(TripPlanGroup group);

    Optional<TripPlanGroup> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from TripPlanGroup g where g.id = :id")
    Optional<TripPlanGroup> findByIdForUpdate(@Param("id") UUID id);
}
