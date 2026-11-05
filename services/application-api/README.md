# Application API

This Spring Boot service owns account registration, login/session issuance, JWT-protected user endpoints, and persistence. It is an independent Maven application; it is not started by the root Docker Compose file.

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
- `GET /api/places/search?q=...&lat=...&lon=...&zoom=...`: public place/POI search; the configured provider returns a bounded set of suggestions biased toward the current map view.

Web session endpoints also check the request Origin. CORS is restricted to configured allowed origins. The API is stateless for access-token authentication.

## Persistence and Configuration

Flyway migrations in `src/main/resources/db/migration/` create `users`, `user_identities`, and `refresh_sessions`. Configure a PostgreSQL datasource with Spring Boot's `spring.datasource.*` properties, for example through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

The checked-in `src/main/resources/application.yaml` contains the service defaults and binds environment overrides. The API binds settings under `app.login` and `app.registration`; environment-variable equivalents use the `APP_LOGIN_...` and `APP_REGISTRATION_...` prefixes. Configure at least:

- Login: `signing-secret`, `issuer`, `audience`, access/refresh token TTLs, allowed origins, cookie security, and cookie name.
- Registration: Google client IDs, JWK set URI, issuers, and allowed origins.
- Place search: `PLACE_SEARCH_PROVIDER`, `PLACE_SEARCH_BASE_URL`, `PLACE_SEARCH_TIMEOUT`, and `PLACE_SEARCH_MAX_RESULTS`. The current provider is Photon; search requests go through this API so the provider can be replaced centrally.

`signing-secret` must be Base64-encoded random bytes with at least 32 decoded bytes. Do not commit credentials or secrets. Supply credentials and environment-specific values through the runtime environment or an external configuration source.

## Run and Test

Use Java 25. From `services/application-api/`:

```sh
./mvnw spring-boot:run
./mvnw test
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. Tests include database-backed integration coverage using Testcontainers, so Docker must be available for those tests.
