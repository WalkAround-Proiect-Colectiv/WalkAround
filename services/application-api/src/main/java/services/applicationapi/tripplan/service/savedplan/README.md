# Saved trip plans

`SavedTripPlanService` exposes creation and owner-scoped lookup for stored search presets. `SavedTripPlanServiceImpl` validates and normalizes the request, creates a `TripPlan` entity, and maps it to `TripPlanResponse`. It does not call SerpApi and never saves flight itineraries, hotel offers, booking links, or prices returned by providers. Flight and accommodation prices must be searched again when a user opens a plan.

## Stored preset

The preset contains a title, optional description, origin airport IATA code, **departure city and country code**, destination city and country code, inclusive local flight departure interval, staying nights, ticket option, EUR flight budget, and EUR accommodation budget. The departure city and country code come from a GeoNames city suggestion and can later be passed to the local city-to-airports lookup when the user wants another origin airport in that city. It intentionally has no accommodation check-in date: that date depends on the flight the user eventually selects and is supplied only when searching accommodations.

The service checks the departure interval order and the same configured maximum calendar-date span used by flight search. It verifies both country codes against the imported EU member-country table, validates both city/country pairs through GeoNames, and stores the canonical city names. `CityAirportService` confirms the selected origin IATA code appears in the departure city's local airport results. The departure country code is stored with the city because the airport lookup requires both. This validation is for search inputs, not availability: a saved plan may stop producing offers as dates and provider data change.

## Ownership and failure behavior

The controller derives the user ID from the authenticated application JWT. Creation requires that the user still exists; clients cannot supply or change an owner ID. Lookup uses `findByIdAndUser_Id`, so another user's plan and a nonexistent plan produce the same `404`. A plan is created only after all input and GeoNames validation succeeds. GeoNames failure returns a generic `502` without storing a plan.

`TripPlanRepository.save` persists the entity in a repository transaction. GeoNames validation runs before that database write, so no database transaction is held during the external request. The create endpoint returns `201` with a `Location` header; the view endpoint returns `200`. Both responses use `Cache-Control: no-store`. List, edit, and delete operations are outside this package's current scope.

Flyway V4 creates `trip_plans` with required `departure_city` and `departure_country_code` columns. Save requests require both departure fields.
