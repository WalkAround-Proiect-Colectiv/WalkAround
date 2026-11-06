package services.applicationapi.placesearch.service;

import java.util.List;
import services.applicationapi.placesearch.dto.PlaceSearchResult;

public interface PlaceSearchProvider {

    List<PlaceSearchResult> search(String query, double latitude, double longitude, int zoom, int limit);
}
