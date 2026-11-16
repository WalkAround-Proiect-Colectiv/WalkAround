package services.applicationapi.tripplan.service.trainsearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import services.applicationapi.tripplan.dto.TrainJourneyResponse;

public interface BiloTrainProvider {

    List<Offer> search(String origin, String destination, LocalDate date);

    record Offer(LocalDateTime departure, LocalDateTime arrival, BigDecimal price,
                 String currency, boolean estimated, boolean partialCatalog,
                 String url, TrainJourneyResponse journey) {
    }
}
