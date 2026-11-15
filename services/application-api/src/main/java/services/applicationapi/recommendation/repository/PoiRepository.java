package services.applicationapi.recommendation.repository;

import java.util.List;
import java.util.Set;
import services.applicationapi.recommendation.domain.PoiCandidate;

public interface PoiRepository {

    List<PoiCandidate> findNearbyPois(
            double latitude,
            double longitude,
            int radiusMeters,
            int limit,
            Set<String> poiClasses,
            Set<String> landcoverClasses,
            Set<String> waterClasses
    );
}
