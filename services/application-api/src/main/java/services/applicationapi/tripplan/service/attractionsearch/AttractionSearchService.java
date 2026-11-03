package services.applicationapi.tripplan.service.attractionsearch;

import java.util.List;
import services.applicationapi.tripplan.dto.AttractionResponse;

public interface AttractionSearchService {

    List<AttractionResponse> search(String city, String countryCode);
}
