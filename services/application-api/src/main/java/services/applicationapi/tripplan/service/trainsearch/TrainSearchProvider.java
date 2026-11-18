package services.applicationapi.tripplan.service.trainsearch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import services.applicationapi.tripplan.dto.TrainJourneyResponse;
import services.applicationapi.tripplan.dto.TrainStopResponse;

public interface TrainSearchProvider {

    long findCityId(String city, String countryCode);

    SearchPage search(long originId, long destinationId, LocalDate date, LocalDate returnDate,
                      int page, int adults);

    List<ReturnOffer> returns(String searchId, String tripId);

    TrainStopResponse findStop(long positionId);

    record SearchPage(String searchId, List<TrainOffer> offers, boolean hasMore) {
    }

    record TrainOffer(String tripId, long priceMinor, LocalDateTime departure,
                      LocalDateTime arrival, TrainJourneyResponse journey) {
    }

    record ReturnOffer(long totalPriceMinor, TrainJourneyResponse journey) {
    }
}
