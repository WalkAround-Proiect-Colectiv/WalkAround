# Application API

This Spring Boot service owns account registration, login/session issuance, JWT-protected user endpoints, weather-aware activity recommendations, and persistence. It is an independent Maven application; it is not started by the root Docker Compose file.

## Request Flow

1. The frontend obtains a Google ID token and sends it to the API.
2. The API verifies the token with the configured Google decoder, then maps the Google provider subject to a WalkAround user.
3. Login creates a short-lived access JWT and a longer-lived refresh session. The refresh token is returned to mobile clients and stored in SecureStore, or set as an HttpOnly cookie for web clients.
4. Refresh rotates the token. PostgreSQL stores only the refresh-token hash, and logout revokes the associated session.
5. Authenticated requests send the access JWT as a Bearer token; Spring Security validates issuer, audience, expiry, and token use.

## Main Endpoints

- `POST /api/registrations/google`: register a Google identity; returns `201` for a new account.
- `POST /api/sessions/google`, `/refresh`, `/logout`: web session endpoints using an HttpOnly cookie.
- `POST /api/mobile/sessions/google`, `/refresh`, `/logout`: mobile session endpoints using a refresh token in JSON.
- `GET /api/users/me`: return the current authenticated user's profile.
- `/api/events/...`: upcoming events near the user, marked events, and reminders. See the [events README](src/main/java/services/applicationapi/events/README.md).
- `GET /api/places/search?q=...&lat=...&lon=...&zoom=...`: public place/POI search; the configured provider returns suggestions biased toward the current map view and restricted to the downloaded Romania coverage. The Photon adapter sends a hard bounding-box filter (`19.5,43.4,30,48.5`) and `countrycode=ro`, then validates each returned coordinate and country code before exposing it. This lets a local feature such as a street named “Paris” appear while excluding the Paris in France. The frontend applies the same coverage check as a final guard. The bounding box represents the map's configured coverage extent; the country filter excludes results from other countries within the rectangle.
- `GET /api/v1/recommendations/activities`: return nearby activities suited to the current weather (public, no authentication required). 

Web session endpoints also check the request Origin. CORS is restricted to configured allowed origins. The API is stateless for access-token authentication.

## Persistence and Configuration

Flyway migrations in `src/main/resources/db/migration/` create `users`, `user_identities`, `refresh_sessions`, and the activity recommendation tables `activity_category` and `weather_activity_rule`, along with their seed rules. Configure a PostgreSQL datasource with Spring Boot's `spring.datasource.*` properties, for example through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

The checked-in `src/main/resources/application.yaml` contains the service defaults and binds environment overrides. The API binds settings under `app.login`, `app.registration`, and `recommendation`; environment-variable equivalents use the `APP_LOGIN_...` and `APP_REGISTRATION_...` prefixes. Configure at least:

- Login: `signing-secret`, `issuer`, `audience`, access/refresh token TTLs, allowed origins, cookie security, and cookie name.
- Registration: Google client IDs, JWK set URI, issuers, and allowed origins.
- Place search: `PLACE_SEARCH_PROVIDER`, `PLACE_SEARCH_BASE_URL`, `PLACE_SEARCH_TIMEOUT`, and `PLACE_SEARCH_MAX_RESULTS`. The current provider is Photon; search requests go through this API so the provider can be replaced centrally. Photon filtering uses the Romania coverage rectangle and ISO country code `ro`; update these filters if the downloaded map coverage changes.
- Recommendations (all optional, defaults shown; see `.env.example`):
  - `RECOMMENDATION_DEFAULT_RADIUS_METERS` (`2000`), `RECOMMENDATION_MAX_RADIUS_METERS` (`5000`)
  - `RECOMMENDATION_DEFAULT_LIMIT` (`15`), `RECOMMENDATION_MAX_LIMIT` (`50`)
  - `RECOMMENDATION_CACHE_TTL` (`PT15M`): weather cache lifetime
  - `OPEN_METEO_BASE_URL` (`https://api.open-meteo.com`), `OPEN_METEO_TIMEOUT` (`PT5S`)
  - `POI_DATABASE_URL`, `POI_DATABASE_USERNAME` (`map_tiles`), `POI_DATABASE_PASSWORD`: a read-only JDBC connection to the `map-data` PostGIS database. Locally it is exposed on host port `15434`, for example `jdbc:postgresql://localhost:15434/map`. If `POI_DATABASE_URL` is empty, the main datasource is used.

`signing-secret` must be Base64-encoded random bytes with at least 32 decoded bytes. Do not commit credentials or secrets. Supply credentials and environment-specific values through the runtime environment or an external configuration source.

## Run and Test

Use Java 25. From `services/application-api/`:

```sh
./mvnw spring-boot:run
./mvnw test
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. Tests include database-backed integration coverage using Testcontainers, so Docker must be available for those tests.
