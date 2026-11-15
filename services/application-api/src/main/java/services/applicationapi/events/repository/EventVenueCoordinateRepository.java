package services.applicationapi.events.repository;

import java.util.Optional;
import services.applicationapi.events.model.EventCoordinates;

/** Looks up geographic coordinates for an event venue in the PostGIS OpenStreetMap database. */
public interface EventVenueCoordinateRepository {

    Optional<EventCoordinates> findVenueCoordinates(String venueName, Double hintLatitude, Double hintLongitude);
}
