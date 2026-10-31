package services.applicationapi.tripplan.service;

import java.util.List;
import services.applicationapi.tripplan.dto.CitySuggestion;

public interface CitySearchProvider {

    List<CitySuggestion> searchByPrefix(String prefix, int limit);
}
