package services.applicationapi.user.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "user_profile_pictures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfilePicture {
    @Id
    private UUID userId;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] imageData;

    public UserProfilePicture(UUID userId, byte[] imageData) {
        this.userId = userId;
        this.imageData = imageData;
    }
}
