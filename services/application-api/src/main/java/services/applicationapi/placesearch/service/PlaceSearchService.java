package services.applicationapi.placesearch.service;

import java.util.List;
import org.springframework.stereotype.Service;
import services.applicationapi.placesearch.config.PlaceSearchProperties;
import services.applicationapi.placesearch.dto.PlaceSearchResult;

@Service
public class PlaceSearchService {

    private final PlaceSearchProvider provider;
    private final PlaceSearchProperties properties;

    public PlaceSearchService(PlaceSearchProvider provider, PlaceSearchProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    public List<PlaceSearchResult> search(String query, double latitude, double longitude, int zoom) {
        if (query == null || query.isBlank() || query.trim().length() < 2 || query.length() > 120) {
            throw new InvalidPlaceSearchException("Search text must contain between 2 and 120 characters");
        }
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new InvalidPlaceSearchException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new InvalidPlaceSearchException("Longitude must be between -180 and 180");
        }
        if (zoom < 0 || zoom > 22) {
            throw new InvalidPlaceSearchException("Zoom must be between 0 and 22");
        }
        return provider.search(query.trim(), latitude, longitude, zoom, properties.maxResults());
    }
}
