# Local city airports

[`CityAirportServiceImpl`](CityAirportServiceImpl.java) backs `GET /api/trip-plans/cities/{city}/airports?countryCode=XX`. It reads municipalities in the requested country, finds matching municipality IDs in Java, then fetches their airports with `AirportRepository`. The city name is a regular URL path value: clients must URL-encode spaces and non-ASCII characters.

Matching has two stages:

1. Compare the normalized base name without folding accents. The base-name step removes trailing parenthetical text and slash suffixes, so `City`, `City (something)`, `City/something`, and `City / something` can all belong to the same exact match.
2. If stage one finds nothing, compare folded keys from [`CityNameNormalizer`](../utils/CityNameNormalizer.java). This handles many Latin/Greek diacritics, selected letter variants, and punctuation. If folded matching points to different exact base names, the service raises `AmbiguousCitySelectionException` instead of silently choosing one.

The repository query is intentionally simple; the normalization is performed in Java, so PostgreSQL `unaccent` is not required. The service returns `[]` if no municipality or airport matches. The `AirportResponse` may contain a null IATA code because the database column is nullable.

The import script `scripts/airports_data/import_airports.py` currently loads EU member countries and **large airports only**. This endpoint describes that local dataset; it is not the destination-airport resolver for flight search, which instead passes a Google Flights city ID to SerpApi.
