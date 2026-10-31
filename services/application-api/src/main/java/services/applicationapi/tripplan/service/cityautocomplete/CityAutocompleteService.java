package services.applicationapi.tripplan.service.cityautocomplete;

import java.util.List;
import services.applicationapi.tripplan.dto.CitySuggestion;

public interface CityAutocompleteService {

    List<CitySuggestion> autocomplete(String city);
}
