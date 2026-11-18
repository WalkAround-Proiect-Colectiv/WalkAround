package services.applicationapi.tripplan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trip_plan_group_pictures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlanGroupPicture {

    @Id
    private UUID groupId;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] imageData;

    public TripPlanGroupPicture(UUID groupId, byte[] imageData) {
        this.groupId = groupId;
        this.imageData = imageData;
    }
}
