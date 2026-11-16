package services.applicationapi.tripplan.service.bussearch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import services.applicationapi.tripplan.dto.BusJourneyResponse;
import services.applicationapi.tripplan.dto.BusStopResponse;

public interface BusSearchProvider {

    long findCityId(String city, String countryCode);

    SearchPage search(long originId, long destinationId, LocalDate date, LocalDate returnDate, int page);

    List<ReturnOffer> returns(String searchId, String tripId);

    BusStopResponse findStop(long positionId);

    record SearchPage(String searchId, List<BusOffer> offers, boolean hasMore) {
    }

    record BusOffer(String tripId, long priceMinor, LocalDateTime departure,
                    LocalDateTime arrival, BusJourneyResponse journey) {
    }

    record ReturnOffer(long totalPriceMinor, BusJourneyResponse journey) {
    }
}
