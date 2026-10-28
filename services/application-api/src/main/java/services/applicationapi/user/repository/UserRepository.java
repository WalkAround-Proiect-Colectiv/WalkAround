package services.applicationapi.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.user.model.User;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {}
