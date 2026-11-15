package services.applicationapi.events.service.reminders;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.EventInterest;
import services.applicationapi.events.repository.EventInterestRepository;

/**
 * Every minute, publishes reminders whose time has come and records {@code reminded_at} so each reminder
 * is sent once. A reminder that fails to publish stays due and is retried on the next run.
 */
@Component
public class EventReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EventReminderJob.class);
    private static final int BATCH_SIZE = 100;

    private final EventInterestRepository interests;
    private final ReminderPublisher publisher;
    private final EventsProperties properties;
    private final Clock clock;

    public EventReminderJob(
            EventInterestRepository interests,
            ReminderPublisher publisher,
            EventsProperties properties,
            Clock eventsClock
    ) {
        this.interests = interests;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = eventsClock;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT20S")
    @Transactional
    public void runScheduled() {
        if (properties.remindersEnabled()) {
            sendDueReminders();
        }
    }

    /** Returns the number of reminders published. */
    @Transactional
    public int sendDueReminders() {
        Instant now = clock.instant();
        List<EventInterest> due = interests.findDueReminders(now, Limit.of(BATCH_SIZE));
        int sent = 0;
        for (EventInterest interest : due) {
            try {
                publisher.publish(interest);
                interest.markReminded(now);
                interests.save(interest);
                sent++;
            } catch (RuntimeException exception) {
                log.warn("Reminder for event {} not delivered yet: {}", interest.getEventKey(), exception.getMessage());
                break;
            }
        }
        return sent;
    }
}
