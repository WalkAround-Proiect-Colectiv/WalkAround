package services.routingservice.savedroutes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import services.routingservice.savedroutes.model.SavedRoute;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedRouteRepository extends JpaRepository<SavedRoute, Long>
{
    List<SavedRoute> findAllByUserIdOrderByCreatedAtDesc(String userId);
    Optional<SavedRoute> findByIdAndUserId(Long id, String userId);
}
