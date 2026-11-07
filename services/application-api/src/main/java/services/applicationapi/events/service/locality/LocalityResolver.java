package services.applicationapi.events.service.locality;

import java.util.Optional;
import services.applicationapi.events.model.Locality;

/** Finds the city, town or commune that contains a point. */
public interface LocalityResolver {

    Optional<Locality> resolve(double latitude, double longitude);
}
