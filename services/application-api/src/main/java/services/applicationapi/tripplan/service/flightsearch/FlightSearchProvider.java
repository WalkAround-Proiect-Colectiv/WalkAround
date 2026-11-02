package services.applicationapi.tripplan.service.flightsearch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import services.applicationapi.tripplan.dto.FlightSegmentResponse;

public interface FlightSearchProvider {

    List<FlightOffer> searchOneWay(
            List<String> departureAirports,
            List<String> arrivalAirports,
            LocalDate date,
            BigDecimal maxPrice,
            LocalTime earliestDeparture,
            LocalTime latestDeparture
    );

    List<FlightOffer> searchRoundTrip(
            String departureAirport,
            List<String> arrivalAirports,
            LocalDate outboundDate,
            LocalDate returnDate,
            BigDecimal maxPrice,
            LocalTime earliestDeparture,
            LocalTime latestDeparture
    );

    List<FlightOffer> searchReturns(
            String departureAirport,
            List<String> arrivalAirports,
            LocalDate outboundDate,
            LocalDate returnDate,
            BigDecimal maxPrice,
            String departureToken
    );

    record FlightOffer(
            BigDecimal price,
            List<FlightSegmentResponse> segments,
            String departureToken,
            String viewUrl
    ) {
    }
}
