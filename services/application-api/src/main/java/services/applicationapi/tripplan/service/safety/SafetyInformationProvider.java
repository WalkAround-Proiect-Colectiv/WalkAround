package services.applicationapi.tripplan.service.safety;

import services.applicationapi.tripplan.dto.DestinationSafetyResponse;

public interface SafetyInformationProvider {
    DestinationSafetyResponse fetch(Location location);

    record Location(String city, String country, String countryCode, double latitude, double longitude) {
    }
}
