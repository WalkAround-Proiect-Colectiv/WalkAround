package services.applicationapi.recommendation.service;

import java.util.List;
import services.applicationapi.recommendation.domain.ActivityCategoryRule;
import services.applicationapi.recommendation.domain.PoiCandidate;

public interface PoiLookupService {

    List<PoiCandidate> findNearbyPois(
            double latitude,
            double longitude,
            int radiusMeters,
            int limit,
            List<ActivityCategoryRule> categories
    );
}
