package services.applicationapi.events.repository;

import java.util.Optional;
import org.springframework.data.repository.Repository;
import services.applicationapi.events.model.EventLocalitySnapshot;

public interface EventLocalitySnapshotRepository extends Repository<EventLocalitySnapshot, String> {

    EventLocalitySnapshot save(EventLocalitySnapshot snapshot);

    Optional<EventLocalitySnapshot> findById(String localityKey);
}
