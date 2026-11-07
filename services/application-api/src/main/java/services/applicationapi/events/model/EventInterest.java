package services.applicationapi.events.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import services.applicationapi.user.model.User;

/** An event a user marked as interesting, with a snapshot of its details and the reminder state. */
@Entity
@Table(name = "event_interests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventInterest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String eventKey;

    @Column(nullable = false, length = 200)
    private String localityKey;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private boolean allDay;

    @Column(length = 200)
    private String whenText;

    @Column(length = 300)
    private String venueName;

    @Column(length = 500)
    private String address;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(length = 100)
    private String ticketSource;

    @Column(columnDefinition = "TEXT")
    private String ticketUrl;

    private Integer remindBeforeMinutes;

    private Instant remindAt;

    private Instant remindedAt;

    private Instant reminderSeenAt;

    @Column(nullable = false)
    private Instant createdAt;

    public EventInterest(User user, String eventKey, String localityKey, EventSnapshot event, Instant now) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.eventKey = eventKey;
        this.localityKey = localityKey;
        this.createdAt = now;
        applySnapshot(event);
    }

    public void applySnapshot(EventSnapshot event) {
        this.title = truncate(event.title(), 300);
        this.startsAt = event.startsAt();
        this.allDay = event.allDay();
        this.whenText = truncate(event.whenText(), 200);
        this.venueName = truncate(event.venueName(), 300);
        this.address = truncate(event.address(), 500);
        this.description = event.description();
        this.thumbnailUrl = event.thumbnailUrl();
        this.ticketSource = truncate(event.ticketSource(), 100);
        this.ticketUrl = event.ticketUrl();
    }

    /**
     * Sets or clears the reminder. A reminder whose time has already passed fires on the next job run,
     * as long as the event itself has not started.
     */
    public void setReminder(Integer minutesBefore, Instant now) {
        if (minutesBefore == null) {
            this.remindBeforeMinutes = null;
            this.remindAt = null;
            this.remindedAt = null;
            this.reminderSeenAt = null;
            return;
        }
        Instant target = startsAt.minus(Duration.ofMinutes(minutesBefore));
        boolean changed = !minutesBefore.equals(this.remindBeforeMinutes);
        this.remindBeforeMinutes = minutesBefore;
        this.remindAt = target.isBefore(now) ? now : target;
        if (changed) {
            this.remindedAt = null;
            this.reminderSeenAt = null;
        }
    }

    public void markReminded(Instant now) {
        this.remindedAt = now;
    }

    public void markReminderSeen(Instant now) {
        this.reminderSeenAt = now;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
