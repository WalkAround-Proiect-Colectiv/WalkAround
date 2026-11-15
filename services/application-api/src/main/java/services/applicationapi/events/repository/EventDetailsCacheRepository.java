package services.applicationapi.events.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import services.applicationapi.events.model.EventDetailsCacheEntry;

public interface EventDetailsCacheRepository extends JpaRepository<EventDetailsCacheEntry, String> {

    @Modifying
    @Query("DELETE FROM EventDetailsCacheEntry e WHERE e.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
