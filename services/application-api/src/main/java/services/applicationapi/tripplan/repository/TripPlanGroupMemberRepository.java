package services.applicationapi.tripplan.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import services.applicationapi.tripplan.model.TripPlanGroupMember;

public interface TripPlanGroupMemberRepository extends Repository<TripPlanGroupMember, UUID> {

    TripPlanGroupMember save(TripPlanGroupMember member);

    boolean existsByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    Optional<TripPlanGroupMember> findByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    List<TripPlanGroupMember> findByUser_IdOrderByJoinedAtDesc(UUID userId);

    @Query("select member.user.id from TripPlanGroupMember member where member.group.id = :groupId")
    List<UUID> findUserIdsByGroupId(@Param("groupId") UUID groupId);
}
