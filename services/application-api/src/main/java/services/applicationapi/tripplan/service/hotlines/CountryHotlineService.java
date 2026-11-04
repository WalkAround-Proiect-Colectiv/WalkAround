package services.applicationapi.tripplan.service.hotlines;

import services.applicationapi.tripplan.dto.CountryHotlineResponse;

public interface CountryHotlineService {

    CountryHotlineResponse get(String countryCode);
}
