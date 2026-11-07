package services.applicationapi.events.service;

import java.util.UUID;
import services.applicationapi.events.model.EventDetails;

/** Builds the complete merged event details view for a user, combining cache, interest and provider details. */
public interface EventDetailsService {

    EventDetails getDetails(UUID userId, String localityKey, String eventKey);
}
