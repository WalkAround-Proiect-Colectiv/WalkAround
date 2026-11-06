package services.applicationapi.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import services.applicationapi.user.model.UserProfilePicture;
import java.util.UUID;

public interface UserProfilePictureRepository extends JpaRepository<UserProfilePicture, UUID> {}
