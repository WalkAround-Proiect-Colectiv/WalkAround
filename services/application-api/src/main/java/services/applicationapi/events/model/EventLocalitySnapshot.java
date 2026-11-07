package services.applicationapi.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Cached event list for one locality, stored as JSON so no per-event rows are kept. */
@Entity
@Table(name = "event_locality_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventLocalitySnapshot {

    @Id
    @Column(length = 200)
    private String localityKey;

    @Column(nullable = false, length = 200)
    private String localityName;

    @Column(length = 200)
    private String county;

    @Column(length = 2)
    private String countryCode;

    @Column(nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String eventsJson;

    public EventLocalitySnapshot(String localityKey, String localityName, String county, String countryCode) {
        this.localityKey = localityKey;
        this.localityName = localityName;
        this.county = county;
        this.countryCode = countryCode;
    }

    public void replaceEvents(String eventsJson, Instant fetchedAt) {
        this.eventsJson = eventsJson;
        this.fetchedAt = fetchedAt;
    }
}
