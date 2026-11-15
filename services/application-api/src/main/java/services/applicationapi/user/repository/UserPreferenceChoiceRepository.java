package services.applicationapi.user.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import services.applicationapi.user.model.*;

public interface UserPreferenceChoiceRepository extends JpaRepository<UserPreferenceChoice, UserPreferenceChoiceId> {
    List<UserPreferenceChoice> findByIdUserIdOrderByIdOptionIdAsc(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from UserPreferenceChoice c where c.id.userId = :userId")
    void deleteChoicesForUser(UUID userId);
}
