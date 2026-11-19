# Trip planning configuration

`GroupChatConfiguration` binds `app.group-chat`, enables scheduled outbox polling, and declares a multi-partition Kafka chat topic. Configure the topic, partition count, maximum message length and page size, socket-ticket lifetime, and relay polling/retry limits through the matching `GROUP_CHAT_*` environment keys. The Application API signs short-lived chat tickets with its existing JWT signer; no extra secret is needed. Keep the topic name synchronized with realtime-service.

`GROUP_CHAT_MAX_POLL_OPTIONS` (default 10) and `GROUP_CHAT_MAX_POLL_OPTION_LENGTH` (default 100) limit poll input. Both are bound through `GroupChatProperties` and have matching local `.env` and `.env.example` keys.

`BusSearchConfiguration` and `TrainSearchConfiguration` register separate qualified Scrappa REST clients and bind their respective search properties. Both use the same `SCRAPPA_API_KEY`, `SCRAPPA_BASE_URL`, and `SCRAPPA_TIMEOUT` settings. Each mode has its own provider selector, result limit, departure date range, arrival offset, and page cap. Keep the shared Scrappa keys only once in `application.yaml` and `.env.example`; no provider key is exposed to the frontend.

`BiloTrainConfiguration` registers a separate qualified REST client for Romanian train search. `BILO_API_KEY` is a server-only bearer credential. Without it, the train service searches only Omio. `ExchangeRateConfiguration` registers the Frankfurter REST client used to fetch the latest RON-to-EUR reference rate once per eligible Bilo search. The returned offer retains its original fare currency and amount.

Trip-planning properties bind under `app.trip-plan`; map place-search properties bind under `app.place-search` in `src/main/resources/application.yaml`. The configuration classes register the properties and create qualified `RestClient` beans: `geoNamesRestClient`, `serpApiRestClient`, `scrappaRestClient`, `scrappaTrainRestClient`, `biloTrainRestClient`, `frankfurterRestClient`, `travelRiskRestClient`, `currentsRestClient`, `openMeteoRestClient`, and `photonRestClient`. Each client sets connection and read timeouts. Accommodation, safety, news, weather, bus, and train search reuse GeoNames city validation.
| Environment key | Property / role | Default in `application.yaml` |
| --- | --- | --- |
| `CITY_PROVIDER` | Selects the `CitySearchProvider` implementation | `geonames` |
| `GEONAMES_USERNAME` | GeoNames account username | Required |
| `CITY_AUTOCOMPLETE_MAX_RESULTS` | Maximum city suggestions requested | `10` |
| `NEARBY_AIRPORT_RADIUS_KM` | Straight-line search radius for nearby large airports in the selected city's country, 1 to 500 km | `100` |
| `NEARBY_AIRPORT_MAX_RESULTS` | Maximum nearby airports returned after city matches, 1 to 50 | `5` |
| `GEONAMES_BASE_URL` | GeoNames HTTPS base URL | `https://secure.geonames.org` |
| `GEONAMES_TIMEOUT` | GeoNames connect/read timeout | `PT3S` |
| `PLACE_SEARCH_PROVIDER` | Selects the map place-search provider | `photon` |
| `PLACE_SEARCH_BASE_URL` | Place-search provider HTTPS base URL | `https://photon.komoot.io` |
| `PLACE_SEARCH_TIMEOUT` | Place-search connection/read timeout | `PT10S` |
| `PLACE_SEARCH_MAX_RESULTS` | Maximum POI/place suggestions returned, 1–10 | `5` |
| `FLIGHT_PROVIDER` | Selects SerpApi flight and city providers | `serpapi` |
| `SERP_API_KEY` | SerpApi private API key | Required by the SerpApi flight provider |
| `FLIGHT_SEARCH_MAX_RESULTS` | Maximum itineraries returned and maximum round-trip return-token lookups | `3` |
| `SERP_API_BASE_URL` | SerpApi HTTPS base URL | `https://serpapi.com` |
| `SERP_API_TIMEOUT` | SerpApi connect/read timeout | `PT15S` |
| `FLIGHT_SEARCH_MAX_DATE_RANGE_DAYS` | Maximum calendar dates covered by one departure interval | `7` |
| `FLIGHT_SEARCH_MAX_ARRIVAL_OFFSET_DAYS` | Maximum days between outbound departure and final arrival for round-trip searches, 0 to 7; bounds initial SerpApi searches per departure date | `2` |
| `SCRAPPA_API_KEY` | Private key shared by the Omio bus and train providers | Required when either provider is used |
| `SCRAPPA_BASE_URL` | Scrappa HTTPS base URL | `https://scrappa.co` |
| `SCRAPPA_TIMEOUT` | Scrappa connect/read timeout | `PT30S` |
| `BUS_PROVIDER` / `TRAIN_PROVIDER` | Selects the provider implementation for each mode | `scrappa` |
| `BUS_SEARCH_MAX_RESULTS` / `TRAIN_SEARCH_MAX_RESULTS` | Maximum returned itineraries and round-trip detail lookups per mode | `3` |
| `BUS_SEARCH_MAX_DATE_RANGE_DAYS` / `TRAIN_SEARCH_MAX_DATE_RANGE_DAYS` | Maximum departure dates searched per request | `7` |
| `BUS_SEARCH_MAX_ARRIVAL_OFFSET_DAYS` / `TRAIN_SEARCH_MAX_ARRIVAL_OFFSET_DAYS` | Maximum outbound arrival offset explored for return dates | `2` |
| `BUS_SEARCH_MAX_PAGES_PER_DATE` / `TRAIN_SEARCH_MAX_PAGES_PER_DATE` | Maximum Omio result pages searched for each date pair | `1` |
| `BILO_API_KEY` | Private bearer key for Romanian train searches | Empty; Bilo disabled |
| `BILO_BASE_URL` | Bilo HTTPS API base URL | `https://mcp.bilo.ro/api/v1` |
| `BILO_TIMEOUT` | Bilo connect/read timeout | `PT15S` |
| `BILO_SEARCH_PAGE_SIZE` | Maximum Bilo journeys requested per date, 1 to 50 | `50` |
| `FRANKFURTER_BASE_URL` | Frankfurter HTTPS API base URL for exchange rates | `https://api.frankfurter.dev` |
| `FRANKFURTER_TIMEOUT` | Frankfurter connect/read timeout | `PT5S` |
| `ACCOMMODATION_PROVIDER` | Selects the `AccommodationSearchProvider` implementation | `serpapi` |
| `ACCOMMODATION_SEARCH_MAX_RESULTS` | Maximum accommodation offers returned | `3` |
| `ATTRACTION_PROVIDER` | Selects the shared `AttractionSearchProvider` implementation for all place categories | `serpapi` |
| `SAFETY_PROVIDER` | Selects the `SafetyInformationProvider` implementation | `travel-risk-api` |
| `TRAVEL_RISK_API_KEY` | Private key sent only in the Travel Risk API `X-API-Key` header | Required |
| `TRAVEL_RISK_API_BASE_URL` | Travel Risk API HTTPS base URL | `https://api.travelriskapi.com` |
| `TRAVEL_RISK_API_TIMEOUT` | Travel Risk API connect/read timeout per feed | `PT8S` |
| `SAFETY_NEARBY_RADIUS_KM` | Maximum distance from validated city center for geolocated reports, 1–500 km | `100` |
| `SAFETY_MAX_RESULTS` | Maximum alerts and conflicts shown per list, 1–50 | `10` |

News configuration adds `NEWS_PROVIDER=currents`, the existing private `CURRENTS_API_KEY`, `CURRENTS_API_BASE_URL=https://api.currentsapi.services`, `CURRENTS_API_TIMEOUT=PT8S`, and `NEWS_PAGE_SIZE=20` (valid range 1–300). The key is sent in an authorization header and is required when a news request is made.

Weather configuration adds `WEATHER_PROVIDER=open-meteo`, `OPEN_METEO_BASE_URL=https://api.open-meteo.com`, `OPEN_METEO_TIMEOUT=PT8S`, and `WEATHER_FORECAST_DAYS=14` (valid range 1–14). The free endpoint does not require a key.

The properties constructors reject non-HTTPS provider URLs, non-positive timeouts, and out-of-range limits. Providers require their configured API keys when used. A different flight provider must implement both the flight-search and Flights-city lookup interfaces, or supply equivalent beans. Other providers implement `AccommodationSearchProvider`, `AttractionSearchProvider`, `SafetyInformationProvider`, `NewsFeedProvider`, or `WeatherForecastProvider` for their respective features. Place search has its own qualified `photonRestClient`; safety, news, and weather use separate qualified clients.

Put real credentials only in the local `services/application-api/.env`; keep `services/application-api/.env.example` synchronized with new keys. Never log provider request URLs or raw exceptions that might contain the API key.
Historical weather uses a separate `openMeteoArchiveRestClient` and `HistoricalWeatherProperties`. `OPEN_METEO_ARCHIVE_BASE_URL` defaults to `https://archive-api.open-meteo.com`; `WEATHER_HISTORY_YEARS` defaults to 3 and accepts 1 to 10. The archive client reuses `OPEN_METEO_TIMEOUT`. No API key is required.

`TripGroupConfiguration` binds `TripGroupProperties` under `app.trip-groups`. `GROUP_PICTURE_MAX_BYTES` defaults to 2 MiB, `GROUP_PICTURE_MAX_PIXELS` to 12 million, and `GROUP_PICTURE_OUTPUT_SIZE` to 512 pixels. `GROUP_INVITE_TTL` defaults to `P7D` and `GROUP_MAX_ACTIVE_INVITES` to 10. The service validates images after multipart parsing; the application global multipart limit still applies. Keep local `.env` and `.env.example` keys aligned.

`GROUP_PLAN_NOTIFICATION_TOPIC` defaults to `trip-group-plan-notifications-topic` and must match the realtime service. It carries per-user messages after a group plan save commits.
