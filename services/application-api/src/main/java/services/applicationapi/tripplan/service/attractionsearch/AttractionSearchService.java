package services.applicationapi.tripplan.service.attractionsearch;

import services.applicationapi.tripplan.dto.AttractionPageResponse;

public interface AttractionSearchService {

    AttractionPageResponse search(String city, String countryCode, int page);
}
