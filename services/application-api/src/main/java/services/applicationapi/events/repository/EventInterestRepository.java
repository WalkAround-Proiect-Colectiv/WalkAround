package services.applicationapi.events.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import services.applicationapi.events.model.EventInterest;

public interface EventInterestRepository extends Repository<EventInterest, UUID> {

    EventInterest save(EventInterest interest);

    void delete(EventInterest interest);

    Optional<EventInterest> findByUser_IdAndEventKey(UUID userId, String eventKey);

    List<EventInterest> findByUser_IdAndStartsAtAfterOrderByStartsAtAsc(UUID userId, Instant after);

    @Query("""
            select i from EventInterest i
            where i.user.id = :userId and i.remindedAt is not null and i.reminderSeenAt is null and i.startsAt > :now
            order by i.startsAt asc
            """)
    List<EventInterest> findUnseenReminders(UUID userId, Instant now);

    @Query("""
            select i from EventInterest i
            where i.remindAt is not null and i.remindAt <= :now and i.remindedAt is null and i.startsAt > :now
            order by i.remindAt asc
            """)
    List<EventInterest> findDueReminders(Instant now, Limit limit);
}
