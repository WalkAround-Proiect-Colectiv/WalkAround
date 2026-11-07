package services.applicationapi.events.service.reminders;

import services.applicationapi.events.model.EventInterest;

/** Delivers a due reminder to the realtime service. Returns normally only when the reminder was accepted. */
public interface ReminderPublisher {

    void publish(EventInterest interest);
}
