# Trip planning configuration

The properties records bind under `app.trip-plan` in `src/main/resources/application.yaml`. The configuration classes register the properties and create qualified `RestClient` beans: `geoNamesRestClient` and `serpApiRestClient`. Both clients set a connection timeout and a read timeout. Accommodation search reuses these clients.

| Environment key | Property / role | Default in `application.yaml` |
| --- | --- | --- |
| `CITY_PROVIDER` | Selects the `CitySearchProvider` implementation | `geonames` |
| `GEONAMES_USERNAME` | GeoNames account username | Required |
| `CITY_AUTOCOMPLETE_MAX_RESULTS` | Maximum city suggestions requested | `10` |
| `GEONAMES_BASE_URL` | GeoNames HTTPS base URL | `https://secure.geonames.org` |
| `GEONAMES_TIMEOUT` | GeoNames connect/read timeout | `PT3S` |
| `FLIGHT_PROVIDER` | Selects SerpApi flight and city providers | `serpapi` |
| `SERP_API_KEY` | SerpApi private API key | Required by the SerpApi flight provider |
| `FLIGHT_SEARCH_MAX_RESULTS` | Maximum itineraries returned and maximum round-trip return-token lookups | `3` |
| `SERP_API_BASE_URL` | SerpApi HTTPS base URL | `https://serpapi.com` |
| `SERP_API_TIMEOUT` | SerpApi connect/read timeout | `PT15S` |
| `FLIGHT_SEARCH_MAX_DATE_RANGE_DAYS` | Maximum calendar dates covered by one departure interval | `7` |
| `ACCOMMODATION_PROVIDER` | Selects the `AccommodationSearchProvider` implementation | `serpapi` |
| `ACCOMMODATION_SEARCH_MAX_RESULTS` | Maximum accommodation offers returned | `3` |
| `ATTRACTION_PROVIDER` | Selects the `AttractionSearchProvider` implementation | `serpapi` |
| `ATTRACTION_SEARCH_MAX_RESULTS` | Maximum first-page attraction recommendations returned, from 1 to 20 | `10` |

The properties constructors reject non-HTTPS provider URLs, non-positive timeouts, and out-of-range limits. The SerpApi providers require a configured API key. A different flight provider must implement both the flight-search and Flights-city lookup interfaces, or supply equivalent beans. A different accommodation provider implements `AccommodationSearchProvider`; a different attractions provider implements `AttractionSearchProvider`. Attractions reuse the existing qualified SerpApi and GeoNames clients, so no new external URL or timeout setting is needed.

Put real credentials only in the local `services/application-api/.env`; keep `services/application-api/.env.example` synchronized with new keys. Never log provider request URLs or raw exceptions that might contain the API key.
