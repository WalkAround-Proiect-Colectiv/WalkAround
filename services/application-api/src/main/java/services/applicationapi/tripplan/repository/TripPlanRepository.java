package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import services.applicationapi.tripplan.model.TripPlan;

public interface TripPlanRepository extends Repository<TripPlan, UUID> {

    TripPlan save(TripPlan tripPlan);

    Optional<TripPlan> findByIdAndUser_IdAndGroupIsNull(UUID id, UUID userId);

    Optional<TripPlan> findByIdAndGroup_Id(UUID id, UUID groupId);

    List<TripPlan> findByGroup_IdOrderByCreatedAtDesc(UUID groupId);

    boolean existsByGroup_IdAndCurrentTrue(UUID groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TripPlan plan set plan.current = false where plan.group.id = :groupId and plan.current = true")
    void clearCurrent(@Param("groupId") UUID groupId);
}
