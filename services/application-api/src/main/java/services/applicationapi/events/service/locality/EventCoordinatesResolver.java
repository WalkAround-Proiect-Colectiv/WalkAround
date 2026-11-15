package services.applicationapi.events.service.locality;

import java.util.Optional;
import services.applicationapi.events.model.EventCoordinates;

/**
 * Resolves geographic coordinates for an event venue or address using the local PostGIS OpenStreetMap
 * database first and falling back to Photon place search when available.
 */
public interface EventCoordinatesResolver {

    Optional<EventCoordinates> resolve(
            String localityName,
            String venueName,
            String address,
            Double hintLatitude,
            Double hintLongitude
    );
}
