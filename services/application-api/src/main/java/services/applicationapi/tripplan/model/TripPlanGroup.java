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
@Table(name = "trip_plan_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlanGroup {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(nullable = false)
    private Instant createdAt;

    public TripPlanGroup(User creator, String name, String description) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.createdByUser = creator;
        this.createdAt = Instant.now();
    }
}
