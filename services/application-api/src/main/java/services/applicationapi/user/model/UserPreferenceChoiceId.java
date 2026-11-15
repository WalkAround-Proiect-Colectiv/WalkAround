package services.applicationapi.user.model;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserPreferenceChoiceId implements Serializable {
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 80)
    private String optionId;
}
