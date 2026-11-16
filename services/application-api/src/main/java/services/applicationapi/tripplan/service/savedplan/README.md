# Saved trip plans

`SavedTripPlanService` exposes creation and owner-scoped lookup for stored search presets. `SavedTripPlanServiceImpl` validates and normalizes the request, creates a `TripPlan` entity, and maps it to `TripPlanResponse`. It never saves flight, bus, or train itineraries, hotel offers, booking links, or prices returned by providers. Transport and accommodation prices must be searched again when a user opens a plan.

## Stored preset

The preset contains a title, optional description, transport mode (`PLANE`, `BUS`, or `TRAIN`), departure and destination city/country pairs, inclusive local departure interval, staying nights, ticket option, EUR transport budget, and EUR accommodation budget. Plane presets also contain an origin airport IATA code; bus and train presets have no airport. The departure city can later be passed to the local city-to-airports lookup when the user wants another airport. It intentionally has no accommodation check-in date: that date depends on the transport itinerary the user eventually selects and is supplied only when searching accommodations.

The service checks departure interval order and the configured maximum calendar-date span for the selected transport. It verifies both country codes against the imported EU member-country table, validates both city/country pairs through GeoNames, and stores the canonical city names. For Plane only, `CityAirportService` confirms the selected origin IATA code appears in the departure city's local airport results. Bus and Train reject an airport value. This validation is for search inputs, not availability: a saved plan may stop producing offers as dates and provider data change.

## Ownership and failure behavior

The controller derives the user ID from the authenticated application JWT. Creation requires that the user still exists; clients cannot supply or change an owner ID. Lookup uses `findByIdAndUser_Id`, so another user's plan and a nonexistent plan produce the same `404`. A plan is created only after all input and GeoNames validation succeeds. GeoNames failure returns a generic `502` without storing a plan.

`TripPlanRepository.save` persists the entity in a repository transaction. GeoNames validation runs before that database write, so no database transaction is held during the external request. The create endpoint returns `201` with a `Location` header; the view endpoint returns `200`. Both responses use `Cache-Control: no-store`. List, edit, and delete operations are outside this package's current scope.

Flyway V4 creates `trip_plans`; V11 adds the transport mode, renames `flight_budget` to `transport_budget`, and makes the airport conditional. Existing rows become Plane presets and retain their airport and budget. Save requests require both departure fields for every transport.
