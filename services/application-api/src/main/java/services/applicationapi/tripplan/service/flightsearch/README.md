# Flight search

This package assembles priced itineraries for `POST /api/trip-plans/flight-searches`. [`FlightSearchService`](FlightSearchService.java) is the controller-facing interface. [`FlightSearchServiceImpl`](FlightSearchServiceImpl.java) orchestrates validation and results; provider-specific HTTP code stays behind [`DestinationCityValidator`](DestinationCityValidator.java), [`FlightCityProvider`](FlightCityProvider.java), and [`FlightSearchProvider`](FlightSearchProvider.java).

## Destination and input validation

The request uses an origin airport **IATA code**, destination city name, destination country code, EUR budget, inclusive departure datetime interval, number of staying nights, and `ROUND_TRIP` or `TWO_ONE_WAY`.

1. Bean Validation checks field presence, code shapes, budget precision/positivity, and nights range. The service checks interval order and its configured maximum number of calendar dates.
2. `AirportRepository.existsByIataCodeIgnoreCase` verifies the origin against the local airports table. `CountryRepository.findByCode` verifies the country against the imported EU member-country table. The import script currently loads only those countries; if the table's import scope changes, this membership assumption must be revisited.
3. [`GeoNamesDestinationCityValidator`](GeoNamesDestinationCityValidator.java) searches populated places (`featureClass=P`) within that country and compares the returned name or ASCII name with the requested city. This validates the city without relying on the local municipality/airport dataset.
4. [`SerpApiFlightCityProvider`](SerpApiFlightCityProvider.java) queries the Google Flights Autocomplete engine using the validated city and country names. It accepts a matching `type=city` suggestion and returns its Google location ID (`/m/...` or `/g/...`). The flight searches pass that ID as the destination; Google Flights chooses the airports. Spelling comparison uses `CityNameNormalizer`, not language translation.

The separate `cityairport` package is not used to find destination airports for this flow. A budget below EUR 1 returns an empty list after GeoNames validation and before the SerpApi city lookup.

## Search and price assembly

[`SerpApiFlightSearchProvider`](SerpApiFlightSearchProvider.java) calls the `google_flights` engine with the origin IATA code, destination city ID, EUR currency, `max_price`, dates, and an hourly `outbound_times` filter. The service then applies the exact local departure datetime interval to returned flights. Provider results are read from both `best_flights` and `other_flights`.

- **Round trip:** one initial search is made for each departure calendar date. The return date is `outbound date + stayingNights`. Initial results contain outbound legs and `departure_token` values. After sorting qualifying outbound candidates by their initial price, the service follows at most `flightSearchMaxResults` distinct tokens to obtain return legs. The price on the selected return option is treated as the full round-trip price; it must fit the budget.
- **Two one-way tickets:** for each departure date, the service searches outbound one-way flights and, only if any qualify, searches return one-way flights on `outbound date + stayingNights`. It combines outbound/return offers and retains pairs whose **sum** fits the budget. The return airport can be any airport Google Flights associates with the destination city.

The service sorts assembled itineraries by total price and returns at most `flightSearchMaxResults` (default `3`). The result is the cheapest among the offers it explored, not a guarantee of the cheapest flight available anywhere. The return date is derived from the **outbound departure date**, even when an outbound flight arrives after midnight.

## External requests and links

Let `D` be the number of calendar dates in the requested departure interval. A round-trip search makes one SerpApi Flights Autocomplete request, `D` initial flight searches, and at most `flightSearchMaxResults` return-token searches. Two one-way tickets make the autocomplete request plus at most `2 × D` flight searches. GeoNames validation adds one GeoNames request. Repeated identical SerpApi requests may use its cache; avoid adding automatic follow-up calls without considering plan usage and latency.

`viewUrls` in the response come from SerpApi's `search_metadata.google_flights_url`, validated as HTTPS Google Flights URLs. A round trip has one URL; two one-way tickets have outbound and return URLs in that order. If no usable URL is provided, the provider supplies the Google Flights homepage. These are search-page references, not guaranteed direct links to the specific displayed offer. This package does not purchase tickets or retrieve booking options.

## Errors and current limitation

The controller maps invalid searches to `400` and provider failures to `502`. Provider request URLs and API keys must not be logged. The current `SerpApiFlightSearchProvider` treats any top-level `error` field as a failure; SerpApi can also set that field on a successful search with zero results. A future fix should use `search_metadata.status` to distinguish an empty successful search from a genuine upstream error. Keep the external error message out of client responses.
