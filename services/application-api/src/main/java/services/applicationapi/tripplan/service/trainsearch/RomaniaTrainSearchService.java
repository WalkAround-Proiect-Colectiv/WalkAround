package services.applicationapi.tripplan.service.trainsearch;

import java.util.List;
import services.applicationapi.tripplan.dto.TicketOption;
import services.applicationapi.tripplan.dto.TrainItineraryResponse;
import services.applicationapi.tripplan.dto.TrainSearchRequest;

public interface RomaniaTrainSearchService {

    boolean supports(TicketOption ticketOption);

    List<TrainItineraryResponse> search(TrainSearchRequest request, String origin, String destination);
}
