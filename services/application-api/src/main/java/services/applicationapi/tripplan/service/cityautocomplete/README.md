# City autocomplete

[`CityAutocompleteServiceImpl`](CityAutocompleteServiceImpl.java) validates a nonempty prefix of at most 100 characters, rejects control characters, and delegates to [`CitySearchProvider`](CitySearchProvider.java). The configured limit is passed to the provider rather than truncating an arbitrary result set afterward.

The current [`GeoNamesCitySearchProvider`](GeoNamesCitySearchProvider.java) calls `/searchJSON` with `name_startsWith`, `featureClass=P`, `continentCode=EU`, `maxRows`, and the configured username. `P` selects populated places; `EU` selects geographical Europe. This endpoint does not check EU membership or whether a city has an airport. The provider is selected by `app.trip-plan.city-provider=geonames`; a replacement should implement the same interface.

`CitySuggestion` includes a display name, country name/code, coordinates, and `lookupName`. `lookupName` preserves the name in Unicode NFC form while trimming and collapsing whitespace. Clients should URL-encode it when placing it in a path; there is no hyphen-for-space convention. City spelling comparison for other flows is handled separately by [`CityNameNormalizer`](../utils/CityNameNormalizer.java).

A valid GeoNames response with no places becomes an empty list. A transport failure, missing response, or GeoNames `status` field becomes `CitySearchUnavailableException`, which the controller maps to `503`. Provider credentials are never included in the client response or logs.
