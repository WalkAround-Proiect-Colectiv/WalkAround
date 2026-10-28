package services.applicationapi.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.user.model.UserIdentity;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {
    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);
}
