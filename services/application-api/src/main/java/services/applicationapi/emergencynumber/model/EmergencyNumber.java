package services.applicationapi.emergencynumber.model;

import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "emergency_numbers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmergencyNumber {

    @Id
    private Long id;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_record_id", nullable = false)
    private String sourceRecordId;

    @Column(name = "number_type", nullable = false)
    private String numberType;

    @Column(nullable = false)
    private String number;

    @Column(name = "is_general_emergency", nullable = false)
    private boolean generalEmergency;

    private String name;
    private String organization;
    private String category;

    @Column(name = "last_verified")
    private LocalDate lastVerified;

    @Column(name = "verification_status")
    private String verificationStatus;

    private String scope;
    private String geography;
    private String hours;
    private String cost;
    private String target;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> languages;

    private String website;

    @Column(name = "chat_url")
    private String chatUrl;

    private String email;
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_links")
    private List<String> sourceLinks;

    @Column(name = "dataset_version", nullable = false)
    private String datasetVersion;
}
