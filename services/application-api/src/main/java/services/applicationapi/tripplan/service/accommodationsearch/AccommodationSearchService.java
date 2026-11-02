package services.applicationapi.tripplan.service.accommodationsearch;

import java.util.List;
import services.applicationapi.tripplan.dto.AccommodationOfferResponse;
import services.applicationapi.tripplan.dto.AccommodationSearchRequest;

public interface AccommodationSearchService {

    List<AccommodationOfferResponse> search(AccommodationSearchRequest request);
}
