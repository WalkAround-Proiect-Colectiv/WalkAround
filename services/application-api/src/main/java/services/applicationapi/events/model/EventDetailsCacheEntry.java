package services.applicationapi.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-event cache row for extra provider details ({@code event_details_cache}).
 *
 * <p>{@code status} is one of {@code OK}, {@code EMPTY}, or {@code FAILED}.
 */
@Entity
@Table(name = "event_details_cache")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventDetailsCacheEntry {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_EMPTY = "EMPTY";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "event_key", length = 64)
    private String eventKey;

    @Column(name = "locality_key", nullable = false, length = 200)
    private String localityKey;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "details_json", columnDefinition = "TEXT")
    private String detailsJson;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public EventDetailsCacheEntry(
            String eventKey,
            String localityKey,
            String status,
            String detailsJson,
            Instant fetchedAt,
            Instant expiresAt
    ) {
        this.eventKey = eventKey;
        this.localityKey = localityKey;
        this.status = status;
        this.detailsJson = detailsJson;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
    }

    public void update(String localityKey, String status, String detailsJson, Instant fetchedAt, Instant expiresAt) {
        this.localityKey = localityKey;
        this.status = status;
        this.detailsJson = detailsJson;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
    }
}
