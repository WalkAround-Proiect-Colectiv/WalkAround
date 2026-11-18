# Trip-planning exceptions

`InvalidTrainSearchException` represents rejected train input (400). `TrainSearchUnavailableException` represents missing provider configuration or upstream failure (502); its client response is generic.

`InvalidBusSearchException` represents rejected user input (400); `BusSearchUnavailableException` represents a missing key or upstream failure (502). The latter's client response is always generic.

These custom runtime exceptions carry application-level failure categories from services to their controller-specific exception handlers. Client-visible messages describe invalid input; provider failures use generic messages that do not reveal provider responses, credentials, or request URLs.

- Invalid input or selection: `InvalidCityQueryException`, `InvalidCitySelectionException`, `InvalidFlightSearchException`, `InvalidAccommodationSearchException`, `InvalidAttractionSearchException`, `InvalidSafetyInformationException`, `InvalidCountryHotlineRequestException`, `InvalidNewsFeedRequestException`, `InvalidWeatherForecastRequestException`, and `InvalidTripPlanException`.
- Ambiguous city-airport lookup: `AmbiguousCitySelectionException`.
- External provider availability: `CitySearchUnavailableException`, `FlightSearchUnavailableException`, `AccommodationSearchUnavailableException`, `AttractionSearchUnavailableException`, `SafetyInformationUnavailableException`, `NewsFeedUnavailableException`, and `WeatherForecastUnavailableException`.
- Saved-plan access: `TripPlanAuthenticationException` when the authenticated account is unavailable and `TripPlanNotFoundException` for a missing or other user's plan.
- Trip group invites: `TripGroupForbiddenException` for nonadmin invite management, `TripGroupInviteNotFoundException` for unavailable links, and `TripGroupInviteLimitException` when the group has reached its active-link limit.
- Group chat: `GroupChatInvalidMessageException` rejects invalid message text or history cursors; `GroupChatConflictException` rejects reuse of a client message ID with different content or ownership.

The exception handlers determine HTTP status and `ProblemDetail` formatting. A provider may retain a `RestClientException` as a cause for debugging, but that cause must not be serialized to clients.
