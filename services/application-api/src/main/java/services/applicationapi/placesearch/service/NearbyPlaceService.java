package services.applicationapi.placesearch.service;

import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import services.applicationapi.placesearch.dto.NearbyPlacePage;
import services.applicationapi.placesearch.repository.NearbyPlaceRepository;

@Service
public class NearbyPlaceService {

    private static final int PAGE_SIZE = 50;
    private final NearbyPlaceRepository repository;

    public NearbyPlaceService(NearbyPlaceRepository repository) {
        this.repository = repository;
    }

    public NearbyPlacePage search(String type, double latitude, double longitude, int radiusMeters, int page) {
        if (!"police".equals(type)) {
            throw new InvalidPlaceSearchException("Nearby search supports type=police");
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < 43.4 || latitude > 48.5 || longitude < 19.5 || longitude > 30) {
            throw new InvalidPlaceSearchException("Nearby search requires a location inside the Romania coverage area");
        }
        if (radiusMeters < 1 || radiusMeters > 10000) {
            throw new InvalidPlaceSearchException("Radius must be between 1 and 10000 meters");
        }
        if (page < 0 || page == Integer.MAX_VALUE) {
            throw new InvalidPlaceSearchException("Invalid nearby search page");
        }
        try {
            var results = repository.findPolice(latitude, longitude, radiusMeters, (long) page * PAGE_SIZE, PAGE_SIZE + 1);
            boolean hasMore = results.size() > PAGE_SIZE;
            return new NearbyPlacePage(
                    List.copyOf(results.subList(0, Math.min(PAGE_SIZE, results.size()))),
                    hasMore ? page + 1 : null);
        } catch (DataAccessException exception) {
            throw new PlaceSearchUnavailableException("Map database lookup failed: " + exception.getClass().getSimpleName());
        }
    }

}
