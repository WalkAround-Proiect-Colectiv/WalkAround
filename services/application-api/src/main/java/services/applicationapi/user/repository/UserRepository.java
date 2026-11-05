package services.applicationapi.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.user.model.User;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdate(UUID id);
}
