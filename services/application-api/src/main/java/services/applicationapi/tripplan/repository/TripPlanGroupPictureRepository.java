package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import services.applicationapi.tripplan.model.TripPlanGroupPicture;

public interface TripPlanGroupPictureRepository extends Repository<TripPlanGroupPicture, UUID> {

    TripPlanGroupPicture save(TripPlanGroupPicture picture);

    Optional<TripPlanGroupPicture> findById(UUID groupId);

    boolean existsById(UUID groupId);

    void deleteById(UUID groupId);
}
