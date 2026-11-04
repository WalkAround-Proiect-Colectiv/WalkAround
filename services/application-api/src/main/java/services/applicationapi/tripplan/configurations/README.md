# Trip planning configuration

The properties records bind under `app.trip-plan` in `src/main/resources/application.yaml`. The configuration classes register the properties and create qualified `RestClient` beans: `geoNamesRestClient`, `serpApiRestClient`, and `travelRiskRestClient`. Each client sets a connection timeout and a read timeout. Accommodation and safety search reuse GeoNames city validation.

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
| `ATTRACTION_PROVIDER` | Selects the shared `AttractionSearchProvider` implementation for all place categories | `serpapi` |
| `SAFETY_PROVIDER` | Selects the `SafetyInformationProvider` implementation | `travel-risk-api` |
| `TRAVEL_RISK_API_KEY` | Private key sent only in the Travel Risk API `X-API-Key` header | Required |
| `TRAVEL_RISK_API_BASE_URL` | Travel Risk API HTTPS base URL | `https://api.travelriskapi.com` |
| `TRAVEL_RISK_API_TIMEOUT` | Travel Risk API connect/read timeout per feed | `PT8S` |
| `SAFETY_NEARBY_RADIUS_KM` | Maximum distance from validated city center for geolocated reports, 1–500 km | `100` |
| `SAFETY_MAX_RESULTS` | Maximum alerts and conflicts shown per list, 1–50 | `10` |

The properties constructors reject non-HTTPS provider URLs, non-positive timeouts, and out-of-range limits. Provider implementations require their configured API keys. A different flight provider must implement both the flight-search and Flights-city lookup interfaces, or supply equivalent beans. Other providers implement `AccommodationSearchProvider`, `AttractionSearchProvider`, or `SafetyInformationProvider` for their respective features. Place search reuses the existing qualified SerpApi and GeoNames clients; safety search uses a separate qualified Travel Risk API client.

Put real credentials only in the local `services/application-api/.env`; keep `services/application-api/.env.example` synchronized with new keys. Never log provider request URLs or raw exceptions that might contain the API key.
