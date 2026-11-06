package services.applicationapi.preferences.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "preference_questions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenceQuestion {
    @Id
    @Column(length = 50)
    private String id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private int displayOrder;
}
