# Trip-planning exceptions

These custom runtime exceptions carry application-level failure categories from services to their controller-specific exception handlers. Client-visible messages describe invalid input; provider failures use generic messages that do not reveal provider responses, credentials, or request URLs.

- Invalid input or selection: `InvalidCityQueryException`, `InvalidCitySelectionException`, `InvalidFlightSearchException`, `InvalidAccommodationSearchException`, `InvalidAttractionSearchException`, `InvalidSafetyInformationException`, `InvalidCountryHotlineRequestException`, `InvalidNewsFeedRequestException`, and `InvalidTripPlanException`.
- Ambiguous city-airport lookup: `AmbiguousCitySelectionException`.
- External provider availability: `CitySearchUnavailableException`, `FlightSearchUnavailableException`, `AccommodationSearchUnavailableException`, `AttractionSearchUnavailableException`, `SafetyInformationUnavailableException`, and `NewsFeedUnavailableException`.
- Saved-plan access: `TripPlanAuthenticationException` when the authenticated account is unavailable and `TripPlanNotFoundException` for a missing or other user's plan.

The exception handlers determine HTTP status and `ProblemDetail` formatting. A provider may retain a `RestClientException` as a cause for debugging, but that cause must not be serialized to clients.
