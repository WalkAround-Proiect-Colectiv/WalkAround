package services.applicationapi.tripplan.service.safety;

import services.applicationapi.tripplan.dto.DestinationSafetyResponse;

public interface SafetyInformationService {
    DestinationSafetyResponse get(String city, String countryCode);
}
