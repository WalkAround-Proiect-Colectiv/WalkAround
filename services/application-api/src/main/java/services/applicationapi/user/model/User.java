package services.applicationapi.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    private UUID id;
    @Column(length = 50)
    private String username;
    private String firstName;
    private String lastName;
    @Column(columnDefinition = "text")
    private String profilePictureUrl;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    public User(String firstName, String lastName, String profilePictureUrl) {
        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.profilePictureUrl = profilePictureUrl;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }
}
