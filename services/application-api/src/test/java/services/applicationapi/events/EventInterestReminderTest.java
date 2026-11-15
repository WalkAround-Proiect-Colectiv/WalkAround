package services.applicationapi.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import services.applicationapi.events.model.EventInterest;
import services.applicationapi.events.model.EventSnapshot;

class EventInterestReminderTest {

    private static final Instant NOW = Instant.parse("2026-11-05T16:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-11-08T18:00:00Z");

    private static EventInterest interest() {
        EventSnapshot snapshot = new EventSnapshot("Concert", STARTS, false, "Nov 8, 8:00 PM", "Venue", "Address",
                null, null, null, null);
        return new EventInterest(null, "key", "RO/cluj/cluj-napoca", snapshot, NOW);
    }

    @Test
    void schedulesTheReminderBeforeTheEvent() {
        EventInterest interest = interest();

        interest.setReminder(1440, NOW);

        assertThat(interest.getRemindAt()).isEqualTo(STARTS.minus(Duration.ofDays(1)));
        assertThat(interest.getRemindBeforeMinutes()).isEqualTo(1440);
    }

    @Test
    void firesImmediatelyWhenTheReminderTimeHasAlreadyPassed() {
        EventInterest interest = interest();

        interest.setReminder(10080, NOW);

        assertThat(interest.getRemindAt()).isEqualTo(NOW);
    }

    @Test
    void clearingTheReminderRemovesAllReminderState() {
        EventInterest interest = interest();
        interest.setReminder(60, NOW);
        interest.markReminded(NOW);

        interest.setReminder(null, NOW);

        assertThat(interest.getRemindAt()).isNull();
        assertThat(interest.getRemindBeforeMinutes()).isNull();
        assertThat(interest.getRemindedAt()).isNull();
    }

    @Test
    void changingTheReminderAllowsItToFireAgain() {
        EventInterest interest = interest();
        interest.setReminder(60, NOW);
        interest.markReminded(NOW);

        interest.setReminder(1440, NOW);

        assertThat(interest.getRemindedAt()).isNull();
    }

    @Test
    void keepingTheSameReminderDoesNotResendIt() {
        EventInterest interest = interest();
        interest.setReminder(60, NOW);
        interest.markReminded(NOW);

        interest.setReminder(60, NOW);

        assertThat(interest.getRemindedAt()).isEqualTo(NOW);
    }
}
