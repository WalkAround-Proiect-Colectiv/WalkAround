package services.applicationapi.tripplan.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import services.applicationapi.user.model.User;

@Entity
@Table(name = "trip_plan_group_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlanGroupMember {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private TripPlanGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GroupRole role;

    @Column(nullable = false)
    private Instant joinedAt;

    public TripPlanGroupMember(TripPlanGroup group, User user, GroupRole role) {
        this.id = UUID.randomUUID();
        this.group = group;
        this.user = user;
        this.role = role;
        this.joinedAt = Instant.now();
    }
}
