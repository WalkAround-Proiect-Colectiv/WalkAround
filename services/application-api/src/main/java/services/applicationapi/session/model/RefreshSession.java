package services.applicationapi.session.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import services.applicationapi.user.model.User;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 16)
    private String clientType;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant lastUsedAt;
    private Instant revokedAt;

    public RefreshSession(UUID id, User user, String tokenHash, String clientType, Instant now, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.tokenHash = tokenHash;
        this.clientType = clientType;
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    public void rotate(String tokenHash, Instant now) {
        this.tokenHash = tokenHash;
        this.lastUsedAt = now;
    }

    public void revoke(Instant now) { this.revokedAt = now; }
}
