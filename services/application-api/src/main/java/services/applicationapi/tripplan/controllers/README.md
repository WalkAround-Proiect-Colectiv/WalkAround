# Trip planning HTTP endpoints

Controllers delegate to service interfaces. The `exceptionhandlers` package contains controller-specific `@RestControllerAdvice` classes that return `ProblemDetail` responses; upstream error details and credentials are not sent to clients.

| Method and path | Input | Successful response |
| --- | --- | --- |
| `GET /api/trip-plans/cities?city=Par` | City-name prefix | `List<CitySuggestion>` from GeoNames. |
| `GET /api/trip-plans/cities/{city}/airports?countryCode=FR` | Selected city name and two-letter country code | `List<AirportResponse>` from the local database. URL-encode the city path segment, including spaces and non-ASCII characters. |
| `POST /api/trip-plans/flight-searches` | JSON `FlightSearchRequest` | Up to the configured number of `FlightItineraryResponse` objects. Search is read-only; it creates no trip or booking record. |
| `POST /api/trip-plans/accommodation-searches` | JSON `AccommodationSearchRequest` | Up to the configured number of `AccommodationOfferResponse` objects. Search creates no booking. |

The flight-search JSON uses `originAirport` as a three-letter IATA code, `destinationCity`, a two-letter `countryCode`, a positive EUR `budget`, `departureFrom` and `departureUntil` as ISO local datetimes, `stayingNights`, and `ticketOption` (`ROUND_TRIP` or `TWO_ONE_WAY`). The datetime interval is interpreted in the origin airport's local time and its endpoints are inclusive. No UTC offset is accepted by the current request DTO. See [`dto/FlightSearchRequest.java`](../dto/FlightSearchRequest.java) for the exact Bean Validation constraints.

Each itinerary includes segment lists, a total EUR price, and `viewUrls`. A round trip has one Google Flights link. Two one-way tickets have two links, ordered outbound then return. These are search-page references, not guaranteed links to the exact itinerary or a booking transaction. An empty list means no matching result was assembled from the provider responses.

Accommodation search accepts `destinationCity` and `countryCode` from a GeoNames city suggestion, `checkInDate` (ISO date), `stayingNights`, and a total EUR `budget`. For example: `{"destinationCity":"Paris","countryCode":"FR","checkInDate":"2026-12-01","stayingNights":3,"budget":450.00}`. The service validates the city with GeoNames, searches the first SerpApi results page, and fetches details for at most the configured number of affordable properties. Both SerpApi requests use one adult and no children. Each response can include description, address, rating, amenities, and budget-compatible booking-site options. The optional `propertyWebsiteUrl` is the property's own website. A request makes at most one Hotels list search plus one detail search per selected candidate; candidates whose detailed prices exceed the budget are skipped, and successful searches with no eligible properties return `[]`.

Error mapping is endpoint-specific: invalid input is generally `400`, an ambiguous local city-airport match is `409`, unavailable city suggestions are `503`, and flight-search or accommodation-search provider failures are `502`. Unexpected failures return `500`. See the handlers for the precise messages and covered exception types.
