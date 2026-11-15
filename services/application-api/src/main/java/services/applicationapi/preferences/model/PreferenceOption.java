package services.applicationapi.preferences.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "preference_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenceOption {
    @Id
    @Column(length = 80)
    private String id;
    @Column(nullable = false, length = 50)
    private String questionId;
    @Column(nullable = false)
    private String label;
    @Column(nullable = false)
    private int displayOrder;
}
