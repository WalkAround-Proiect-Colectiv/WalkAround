package services.applicationapi.events.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import services.applicationapi.events.configurations.EventsProperties;
import services.applicationapi.events.model.CachedEvent;
import services.applicationapi.events.model.EventInterest;
import services.applicationapi.events.model.EventSnapshot;
import services.applicationapi.events.model.TicketLink;
import services.applicationapi.events.repository.EventInterestRepository;
import services.applicationapi.events.service.exceptions.EventNotFoundException;
import services.applicationapi.events.service.exceptions.EventsAuthenticationException;
import services.applicationapi.events.service.exceptions.InvalidEventRequestException;
import services.applicationapi.events.service.source.EventSource;
import services.applicationapi.events.service.source.RawEventDetails;
import services.applicationapi.user.model.User;
import services.applicationapi.user.repository.UserRepository;

/**
 * Events a user marked as interesting, and their reminders.
 *
 * <p>Marking an event copies it into {@code event_interests}. When the provider has a details token for
 * the event, one details request (one SerpApi search) fills in the exact date with its year, the full
 * address and a description; otherwise the cached list data is kept.
 */
@Service
public class EventInterestService {

    private final EventInterestRepository interests;
    private final UserRepository users;
    private final EventsService eventsService;
    private final EventSource eventSource;
    private final EventsProperties properties;
    private final Clock clock;

    public EventInterestService(
            EventInterestRepository interests,
            UserRepository users,
            EventsService eventsService,
            EventSource eventSource,
            EventsProperties properties,
            Clock eventsClock
    ) {
        this.interests = interests;
        this.users = users;
        this.eventsService = eventsService;
        this.eventSource = eventSource;
        this.properties = properties;
        this.clock = eventsClock;
    }

    @Transactional(readOnly = true)
    public List<EventInterest> upcoming(UUID userId) {
        Instant now = clock.instant();
        return interests.findByUser_IdAndStartsAtAfterOrderByStartsAtAsc(
                userId, LocalDate.ofInstant(now, properties.timeZone()).atStartOfDay(properties.timeZone()).toInstant()
                        .minusSeconds(1));
    }

    @Transactional(readOnly = true)
    public Optional<EventInterest> find(UUID userId, String eventKey) {
        return interests.findByUser_IdAndEventKey(userId, eventKey);
    }

    /** Marks an event, or updates the reminder of an already marked event. */
    @Transactional
    public EventInterest mark(UUID userId, String localityKey, String eventKey, Integer remindBeforeMinutes) {
        Instant now = clock.instant();
        Optional<EventInterest> existing = interests.findByUser_IdAndEventKey(userId, eventKey);
        if (existing.isPresent()) {
            EventInterest interest = existing.get();
            interest.setReminder(remindBeforeMinutes, now);
            return interests.save(interest);
        }
        CachedEvent event = eventsService.findEvent(localityKey, eventKey)
                .orElseThrow(EventNotFoundException::new);
        if (!event.startsAt().plus(EventListBuilder.STARTED_GRACE).isAfter(now)) {
            throw new InvalidEventRequestException("This event has already taken place");
        }
        User user = users.findById(userId).orElseThrow(EventsAuthenticationException::new);
        EventInterest interest = new EventInterest(user, eventKey, localityKey, snapshot(event, now), now);
        interest.setReminder(remindBeforeMinutes, now);
        return interests.save(interest);
    }

    @Transactional
    public void unmark(UUID userId, String eventKey) {
        interests.findByUser_IdAndEventKey(userId, eventKey).ifPresent(interests::delete);
    }

    @Transactional(readOnly = true)
    public List<EventInterest> unseenReminders(UUID userId) {
        return interests.findUnseenReminders(userId, clock.instant());
    }

    @Transactional
    public void markReminderSeen(UUID userId, String eventKey) {
        EventInterest interest = interests.findByUser_IdAndEventKey(userId, eventKey)
                .orElseThrow(EventNotFoundException::new);
        interest.markReminderSeen(clock.instant());
        interests.save(interest);
    }

    private EventSnapshot snapshot(CachedEvent event, Instant now) {
        Optional<RawEventDetails> details = eventSource.details(event.detailsToken());
        Instant startsAt = event.startsAt();
        boolean allDay = event.allDay();
        String whenText = event.whenText();
        String address = event.address();
        String description = null;
        List<TicketLink> tickets = event.tickets();
        if (details.isPresent()) {
            RawEventDetails extra = details.get();
            if (extra.when() != null) {
                Optional<EventDateParser.ParsedDate> parsed = EventDateParser.parse(
                        null, extra.when(), LocalDate.ofInstant(now, properties.timeZone()), properties.timeZone());
                if (parsed.isPresent()) {
                    startsAt = parsed.get().startsAt();
                    allDay = parsed.get().allDay();
                    whenText = extra.when();
                }
            }
            if (extra.address() != null && !extra.address().isEmpty()) {
                address = String.join(", ", extra.address());
            }
            description = extra.description();
            if (extra.tickets() != null && !extra.tickets().isEmpty()) {
                tickets = extra.tickets();
            }
        }
        TicketLink ticket = tickets == null || tickets.isEmpty() ? null : tickets.getFirst();
        return new EventSnapshot(
                event.title(), startsAt, allDay, whenText, event.venueName(), address, description,
                event.thumbnailUrl(), ticket == null ? null : ticket.source(), ticket == null ? null : ticket.url());
    }
}
