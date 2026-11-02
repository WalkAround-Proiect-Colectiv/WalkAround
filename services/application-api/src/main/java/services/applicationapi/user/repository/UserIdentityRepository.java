package services.applicationapi.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.user.model.UserIdentity;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<UserIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);
}
