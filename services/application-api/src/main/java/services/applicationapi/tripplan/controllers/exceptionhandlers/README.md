# Trip-planning exception handlers

`TrainSearchExceptionHandler` maps malformed or invalid train searches to 400, unavailable GeoNames or Scrappa calls to a generic 502, and unexpected failures to a logged generic 500. Provider error bodies and credentials are never returned.

`BusSearchExceptionHandler` maps malformed or invalid bus requests to 400, unavailable Scrappa or GeoNames requests to a generic 502, and unexpected failures to a logged generic 500. No upstream error body reaches the client.

Each `@RestControllerAdvice` is scoped to its own trip-planning controller. Validation and custom domain exceptions become client-friendly `ProblemDetail` responses; unexpected failures are logged and returned as generic `500` responses.

- `CityAutocompleteExceptionHandler` handles invalid city prefixes and GeoNames availability failures.
- `CityAirportExceptionHandler` handles invalid or ambiguous city-airport lookups and returns a generic `502` when GeoNames city coordinates are unavailable.
- `FlightSearchExceptionHandler` handles invalid flight searches and provider failures.
- `AccommodationSearchExceptionHandler` handles invalid accommodation searches (`400`) and GeoNames or SerpApi failures (`502`). It never includes external provider error text in responses.
- `AttractionSearchExceptionHandler` handles missing or invalid city/country/page input (`400`) for the place categories, GeoNames or SerpApi failures (`502`), and unexpected failures (`500`). It never returns provider error text.
- `SafetyInformationExceptionHandler` handles invalid safety requests (`400`), unavailable GeoNames or Travel Risk API data (`502`), and unexpected failures (`500`). It never returns provider error text or credentials.
- `CountryHotlineExceptionHandler` handles invalid or unsupported country codes (`400`) and unexpected local lookup failures (`500`) without exposing database details.
- `NewsFeedExceptionHandler` handles invalid city/country input (`400`), unavailable GeoNames or Currents responses (`502`), and unexpected failures (`500`) without exposing provider details.
- `WeatherForecastExceptionHandler` handles invalid city/country input (`400`), unavailable GeoNames or Open-Meteo responses (`502`), and unexpected failures (`500`) without exposing provider details.
- `TripPlanExceptionHandler` handles saved-plan validation (`400`), ambiguous departure-city matches (`409`), unavailable account (`401`), owner-scoped misses (`404`), and GeoNames validation failure (`502`).
- `TripGroupExceptionHandler` handles invalid group or invite input (`400`), oversized multipart requests (`413`), missing authentication (`401`), nonmember or unknown group reads and unavailable invites (`404`), member attempts to edit a group or manage invites (`403`), active-invite limit (`409`), and unexpected failures (`500`). Raw image parsing errors, token hashes, and stack traces do not reach clients.

When adding another endpoint, keep its error mapping scoped to its controller so an exception in one flow does not change responses from another.
`HistoricalWeatherExceptionHandler` scopes client-friendly `400` and generic `502` responses to the historical weather controller, hides upstream error details, and logs unexpected failures.
