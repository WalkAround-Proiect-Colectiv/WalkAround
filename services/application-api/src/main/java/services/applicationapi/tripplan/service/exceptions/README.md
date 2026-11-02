# Trip-planning exceptions

These custom runtime exceptions carry application-level failure categories from services to their controller-specific exception handlers. Client-visible messages describe invalid input; provider failures use generic messages that do not reveal provider responses, credentials, or request URLs.

- Invalid input or selection: `InvalidCityQueryException`, `InvalidCitySelectionException`, `InvalidFlightSearchException`, and `InvalidAccommodationSearchException`.
- Ambiguous city-airport lookup: `AmbiguousCitySelectionException`.
- External provider availability: `CitySearchUnavailableException`, `FlightSearchUnavailableException`, and `AccommodationSearchUnavailableException`.

The exception handlers determine HTTP status and `ProblemDetail` formatting. A provider may retain a `RestClientException` as a cause for debugging, but that cause must not be serialized to clients.
