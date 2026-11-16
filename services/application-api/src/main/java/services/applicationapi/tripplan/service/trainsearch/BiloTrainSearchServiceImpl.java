package services.applicationapi.tripplan.service.trainsearch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import services.applicationapi.tripplan.configurations.BiloTrainProperties;
import services.applicationapi.tripplan.configurations.TrainSearchProperties;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.dto.TrainItineraryResponse;
import services.applicationapi.tripplan.dto.TrainSearchRequest;
import services.applicationapi.tripplan.service.trainsearch.BiloTrainProvider.Offer;

@Service
public class BiloTrainSearchServiceImpl implements RomaniaTrainSearchService {

    private static final Logger log = LoggerFactory.getLogger(BiloTrainSearchServiceImpl.class);

    private final BiloTrainProvider provider;
    private final BiloTrainProperties biloProperties;
    private final TrainSearchProperties trainProperties;

    public BiloTrainSearchServiceImpl(BiloTrainProvider provider, BiloTrainProperties biloProperties,
                                      TrainSearchProperties trainProperties) {
        this.provider = provider;
        this.biloProperties = biloProperties;
        this.trainProperties = trainProperties;
    }

    @Override
    public boolean supports(TicketOption ticketOption) {
        return ticketOption == TicketOption.TWO_ONE_WAY
                && biloProperties.biloApiKey() != null
                && !biloProperties.biloApiKey().isBlank();
    }

    @Override
    public List<TrainItineraryResponse> search(TrainSearchRequest request, String origin,
                                               String destination) {
        if (!supports(request.ticketOption())) return List.of();
        List<TrainItineraryResponse> results = new ArrayList<>();
        Map<LocalDate, List<Offer>> returnDays = new HashMap<>();
        LocalDate lastSupportedDate = LocalDate.now().plusDays(364);

        for (LocalDate date = request.departureFrom().toLocalDate();
                !date.isAfter(request.departureUntil().toLocalDate()); date = date.plusDays(1)) {
            for (Offer outbound : provider.search(origin, destination, date)) {
                if (outbound.departure().isBefore(request.departureFrom())
                        || outbound.departure().isAfter(request.departureUntil())) continue;

                LocalDate returnDate = outbound.arrival().toLocalDate()
                        .plusDays(request.stayingNights());
                if (returnDate.isAfter(lastSupportedDate)) continue;

                List<Offer> returning = returnDays.computeIfAbsent(returnDate,
                        day -> provider.search(destination, origin, day));
                for (Offer returnOffer : returning) {
                    BigDecimal totalEur = eur(outbound).add(eur(returnOffer));
                    if (!returnOffer.departure().toLocalDate().equals(returnDate)
                            || totalEur.compareTo(request.budget()) > 0) continue;

                    boolean sameCurrency = outbound.currency().equals(returnOffer.currency());
                    results.add(new TrainItineraryResponse(TicketOption.TWO_ONE_WAY, "EUR",
                            totalEur.setScale(2, RoundingMode.HALF_UP),
                            outbound.journey(), returnOffer.journey(), "BILO",
                            sameCurrency ? outbound.price().add(returnOffer.price()) : null,
                            sameCurrency ? outbound.currency() : null,
                            outbound.estimated() || returnOffer.estimated(),
                            outbound.partialCatalog() || returnOffer.partialCatalog(),
                            outbound.url(), returnOffer.url()));
                }
            }
        }

        if (results.isEmpty()) {
            log.info("No Bilo train pairs met the departure window, stay length, and EUR budget");
        } else {
            log.debug("Found {} affordable Bilo train pairs", results.size());
        }
        return results.stream()
                .sorted(Comparator.comparing(TrainItineraryResponse::totalPrice))
                .limit(trainProperties.trainSearchMaxResults())
                .toList();
    }

    private BigDecimal eur(Offer offer) {
        return "RON".equals(offer.currency())
                ? offer.price().divide(biloProperties.biloRonPerEur(), 8, RoundingMode.HALF_UP)
                : offer.price();
    }
}
