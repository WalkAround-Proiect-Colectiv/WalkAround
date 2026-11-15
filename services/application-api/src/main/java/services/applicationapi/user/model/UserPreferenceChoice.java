package services.applicationapi.user.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "user_preference_choices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferenceChoice {
    @EmbeddedId
    private UserPreferenceChoiceId id;

    public UserPreferenceChoice(UUID userId, String optionId) {
        this.id = new UserPreferenceChoiceId(userId, optionId);
    }
}
