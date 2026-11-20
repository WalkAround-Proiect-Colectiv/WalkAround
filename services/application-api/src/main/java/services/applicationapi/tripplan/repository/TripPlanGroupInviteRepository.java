package services.applicationapi.tripplan.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import services.applicationapi.tripplan.model.TripPlanGroupInvite;

public interface TripPlanGroupInviteRepository extends Repository<TripPlanGroupInvite, UUID> {

    TripPlanGroupInvite save(TripPlanGroupInvite invite);

    Optional<TripPlanGroupInvite> findByTokenHash(String tokenHash);

    @Query("select invite.group.id from TripPlanGroupInvite invite where invite.tokenHash = :tokenHash")
    Optional<UUID> findGroupIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from TripPlanGroupInvite invite where invite.tokenHash = :tokenHash")
    Optional<TripPlanGroupInvite> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invite from TripPlanGroupInvite invite where invite.id = :id and invite.group.id = :groupId")
    Optional<TripPlanGroupInvite> findByIdAndGroupIdForUpdate(@Param("id") UUID id,
                                                               @Param("groupId") UUID groupId);

    long countByGroup_IdAndUsedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(UUID groupId, Instant now);

    List<TripPlanGroupInvite> findTop50ByGroup_IdOrderByCreatedAtDesc(UUID groupId);
}
