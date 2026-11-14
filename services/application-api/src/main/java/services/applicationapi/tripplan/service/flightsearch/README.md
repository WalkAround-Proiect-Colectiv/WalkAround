# Flight search

This package assembles priced itineraries for `POST /api/trip-plans/flight-searches`. [`FlightSearchService`](FlightSearchService.java) is the controller-facing interface. [`FlightSearchServiceImpl`](FlightSearchServiceImpl.java) orchestrates validation and results; provider-specific HTTP code stays behind [`DestinationCityValidator`](DestinationCityValidator.java), [`FlightCityProvider`](FlightCityProvider.java), and [`FlightSearchProvider`](FlightSearchProvider.java).

## Destination and input validation

The request uses an origin airport **IATA code**, destination city name, destination country code, EUR budget, inclusive departure datetime interval, number of staying nights, and `ROUND_TRIP` or `TWO_ONE_WAY`.

1. Bean Validation checks field presence, code shapes, budget precision/positivity, and nights range. The service checks interval order and its configured maximum number of calendar dates.
2. `AirportRepository.existsByIataCodeIgnoreCase` verifies the origin against the local airports table. `CountryRepository.findByCode` verifies the country against the imported EU member-country table. The import script currently loads only those countries; if the table's import scope changes, this membership assumption must be revisited.
3. [`GeoNamesDestinationCityValidator`](GeoNamesDestinationCityValidator.java) searches populated places (`featureClass=P`) within that country and compares the returned name or ASCII name with the requested city. This validates the city without relying on the local municipality/airport dataset.
4. [`SerpApiFlightCityProvider`](SerpApiFlightCityProvider.java) queries the Google Flights Autocomplete engine using the validated city and country names. It accepts a matching `type=city` suggestion and returns its Google location ID (`/m/...` or `/g/...`). The flight searches pass that ID as the destination; Google Flights chooses the airports. Spelling comparison uses `CityNameNormalizer`, not language translation.

The separate `cityairport` package is not used to find destination airports for this flow. The saved-plan service also uses `DestinationCityValidator` to validate both departure and destination cities when a user stores a search preset. A budget below EUR 1 returns an empty list after GeoNames validation and before the SerpApi city lookup.

## Search and price assembly

[`SerpApiFlightSearchProvider`](SerpApiFlightSearchProvider.java) calls the `google_flights` engine with the origin IATA code, destination city ID, EUR currency, `max_price`, dates, and an hourly `outbound_times` filter. The service then applies the exact local departure datetime interval to returned flights. Provider results are read from both `best_flights` and `other_flights`.

- **Round trip:** for each departure date, the service searches return dates from `outbound date + stayingNights` through that date plus `flightSearchMaxArrivalOffsetDays` (default `2`). It keeps only outbounds whose final arrival date plus `stayingNights` equals the searched return date, then uses the departure token from that matching search to obtain return legs. Searching every permitted offset also covers cases where an earlier return date yields no outbound results. After sorting qualifying outbound candidates by their initial price, the service follows at most `flightSearchMaxResults` distinct tokens. The price on the selected return option is treated as the full round-trip price; it must fit the budget.
- **Two one-way tickets:** for each departure date, the service searches outbound one-way flights. It calculates each return date from the final outbound segment's arrival date plus `stayingNights`, then searches return one-way flights for each distinct date. Return searches are reused when multiple outbounds need the same date. Offers with an unparseable final arrival time are skipped. It combines outbound/return offers and retains pairs whose **sum** fits the budget. The return airport can be any airport Google Flights associates with the destination city.

The service sorts assembled itineraries by total price and returns at most `flightSearchMaxResults` (default `3`). The result is the cheapest among the offers it explored, not a guarantee of the cheapest flight available anywhere. Both ticket options count staying nights from the final outbound arrival date. Outbounds with unparseable arrival times are skipped; round-trip outbounds arriving beyond the configured offset are also skipped.

## External requests and links

Let `D` be the number of calendar dates in the requested departure interval and `A` the configured maximum arrival offset. A round-trip search makes one SerpApi Flights Autocomplete request, `D × (A + 1)` initial flight searches, and at most `flightSearchMaxResults` return-token searches. Two one-way tickets make the autocomplete request, up to `D` outbound searches, and one return search per distinct arrival-based return date. GeoNames validation adds one GeoNames request. Repeated identical SerpApi requests may use its cache; avoid adding automatic follow-up calls without considering plan usage and latency.

`viewUrls` in the response come from SerpApi's `search_metadata.google_flights_url`, validated as HTTPS Google Flights URLs. A round trip has one URL; two one-way tickets have outbound and return URLs in that order. If no usable URL is provided, the provider supplies the Google Flights homepage. These are search-page references, not guaranteed direct links to the specific displayed offer. This package does not purchase tickets or retrieve booking options.

## Empty results and upstream errors

The provider uses `search_metadata.status` as the outcome of a SerpApi flight search. `Success` is processed even when SerpApi includes a top-level `error`; if there are no flight offers, the endpoint returns an empty list. Missing metadata or any status other than `Success` is treated as an upstream failure. The controller maps invalid searches to `400` and provider failures to `502` with a generic message. The provider logs the unsuccessful status, but neither exposes SerpApi's error text to clients nor logs provider request URLs or API keys.
