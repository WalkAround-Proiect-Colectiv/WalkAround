# Trip-planning exception handlers

Each `@RestControllerAdvice` is scoped to its own trip-planning controller. Validation and custom domain exceptions become client-friendly `ProblemDetail` responses; unexpected failures are logged and returned as generic `500` responses.

- `CityAutocompleteExceptionHandler` handles invalid city prefixes and GeoNames availability failures.
- `CityAirportExceptionHandler` handles invalid or ambiguous city-airport lookups.
- `FlightSearchExceptionHandler` handles invalid flight searches and provider failures.
- `AccommodationSearchExceptionHandler` handles invalid accommodation searches (`400`) and GeoNames or SerpApi failures (`502`). It never includes external provider error text in responses.
- `AttractionSearchExceptionHandler` handles missing or invalid city/country/page input (`400`), GeoNames or SerpApi failures (`502`), and unexpected failures (`500`). It never returns provider error text.
- `TripPlanExceptionHandler` handles saved-plan validation (`400`), ambiguous departure-city matches (`409`), unavailable account (`401`), owner-scoped misses (`404`), and GeoNames validation failure (`502`).

When adding another endpoint, keep its error mapping scoped to its controller so an exception in one flow does not change responses from another.
