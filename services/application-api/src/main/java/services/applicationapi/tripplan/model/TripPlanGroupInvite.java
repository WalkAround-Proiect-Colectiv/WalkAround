package services.applicationapi.tripplan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import services.applicationapi.user.model.User;

@Entity
@Table(name = "trip_plan_group_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlanGroupInvite {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private TripPlanGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_user_id")
    private User usedByUser;

    private Instant revokedAt;

    public TripPlanGroupInvite(TripPlanGroup group, User creator, String tokenHash,
                               Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.group = group;
        this.createdByUser = creator;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean activeAt(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void use(User member, Instant now) {
        this.usedByUser = member;
        this.usedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) this.revokedAt = now;
    }
}
