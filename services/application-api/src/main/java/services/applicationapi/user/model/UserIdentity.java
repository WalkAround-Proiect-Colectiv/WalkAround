package services.applicationapi.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserIdentity {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 32)
    private String provider;
    @Column(nullable = false)
    private String providerSubject;
    @Column(nullable = false, length = 320)
    private String email;
    @Column(nullable = false)
    private boolean emailVerified;
    @Column(nullable = false)
    private Instant createdAt;

    public UserIdentity(User user, String provider, String subject, String email) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.provider = provider;
        this.providerSubject = subject;
        this.email = email;
        this.emailVerified = true;
        this.createdAt = Instant.now();
    }
}
