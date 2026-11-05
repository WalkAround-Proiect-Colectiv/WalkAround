# Phase 1 Findings: Weather-Based Activity Recommendations

## 1. Frontend Discovery (`frontend/src/`)

### 1.1 Route Layout and Navigation (`frontend/src/app/`)
- **Router setup**: Uses Expo Router (`expo-router ~58.0.28`) with a root `Stack` in `frontend/src/app/_layout.tsx` (`<SessionProvider><Stack screenOptions={{ headerShown: false }} /></SessionProvider>`).
- **No `(tabs)` group currently exists**: Routes are flat files in `frontend/src/app/` that re-export screen components from feature folders:
  - `src/app/index.tsx` → `<Redirect href="/map" />`
  - `src/app/map.tsx` → `src/map/MapScreen.tsx`
  - `src/app/trip-plan.tsx` → `src/tripplan/pages/TripPlanningScreen.tsx`
  - `src/app/home.tsx` → `src/login/pages/AccountScreen.tsx`
  - `src/app/login.tsx` → `src/login/pages/LoginScreen.tsx`
  - `src/app/register.tsx` → `src/register/pages/RegistrationScreen.tsx`
- **Feature navigation**: Primary navigation between app features happens via the floating hamburger menu (`mapMenu` / `menuPanel`) in `src/map/MapScreen.tsx` (lines 229–247), which uses `lucide-react-native` icons and currently has:
  - *Explore map* (`Compass`)
  - *Plan a trip* (`Route` → `router.push('/trip-plan')`)
  - *My future* (`Heart`, disabled `"Coming soon"` placeholder)
- **Routing decision for Phase 4**:
  - Follow the repository's feature-folder + route re-export pattern by placing feature code in `src/recommendations/` (and hooks in `src/hooks/`), adding `src/app/recommendations.tsx`, and wiring it into `MapScreen.tsx`'s feature menu (or introducing a `(tabs)` layout if preferred).

### 1.2 Existing Location Handling
- **Location implementation**: `frontend/src/map/userLocalization.ts` exports `useUserLocalization(map: RefObject<MapHandle | null>)`.
- **APIs used**: `expo-location` (`~58.0.20`) with `Location.requestForegroundPermissionsAsync()`, `Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.BestForNavigation })`, and `Location.watchPositionAsync(...)`.
- **Coupling & reuse**: `useUserLocalization` is currently coupled to `MapHandle` camera movement, marker animation (`requestAnimationFrame`), and Romania bounding-box enforcement (`isInCoverage` from `src/map/config.ts`).
- **Reuse strategy**: Create a general-purpose `useUserLocation` hook in `src/hooks/useUserLocation.ts` wrapping `expo-location` permission status, current position with timeout, `getLastKnownPositionAsync()` fallback, and `Linking.openSettings()`, so location logic is not duplicated across features.

### 1.3 API Transport Layer
- **Environment config**: `frontend/src/config/environment.ts` reads `EXPO_PUBLIC_API_BASE_URL` (trailing slash stripped), `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`, and `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID`.
- **Transport implementations**:
  - `frontend/src/login/api/http.ts`: unauthenticated `request<T>(path, options)` helper with a 15-second `AbortController` timeout and `ApiError(status, message)`.
  - `frontend/src/login/api/session.ts`: external store (`getSession`, `subscribe`, `refreshSession`) managing the in-memory access token and platform-specific refresh token (SecureStore on native, HttpOnly cookie on web).
  - `frontend/src/tripplan/api/tripPlanning.ts`: authenticated `send<T>(path, init)` helper that checks `environment.apiBaseUrl`, refreshes the session if missing or expiring within 30 seconds, sets `Authorization: Bearer ${session.accessToken}`, parses RFC 7807 `ProblemDetail` (`problem?.detail`), and enforces an `AbortController` timeout.
- **No third-party HTTP/query libraries**: Neither `axios`, `@tanstack/react-query`, nor `swr` is installed. Native `fetch` + `AbortController` + React hooks is the established convention.

### 1.4 UI Component Library, Styling, and Icons
- **Primitives**: Standard React Native (`View`, `Text`, `Pressable`, `ScrollView`, `FlatList`, `ActivityIndicator`, `StyleSheet`, `Linking`) + `react-native-safe-area-context` (`useSafeAreaInsets`).
- **Icons**: `lucide-react-native` (`^1.66.0`).
- **Color palette & tokens** (consistent across `MapScreen.tsx`, `TripPlanningScreen.tsx`, `PlaceRecommendationCard.tsx`):
  - Primary Brand Blue: `#0065EB`
  - Accent Purple: `#23012C`
  - Danger / Error Red: `#d93838` / `#C51B2D` / `#ff0000`
  - Primary Ink: `#202632` / `#263241`
  - Secondary / Muted Text: `#566276` / `#626b78` / `#7A8595`
  - Surfaces & Borders: `#ffffff` cards, `#F6F9FC` screen background, `#EAF3FF` icon/chip tint, `#E3EAF2` / `#dce2e9` borders, `borderRadius: 16–20`.
- **Card reference**: `frontend/src/tripplan/components/PlaceRecommendationCard.tsx` provides the exact card styling and safe external map link handling (`Linking.openURL`).
- **i18n**: No i18n framework is installed; user-facing copy is in English directly in components.

### 1.5 State Management, App-Open Trigger, and Testing Setup
- **App-open / foreground trigger**: `frontend/src/login/components/SessionProvider.tsx` (mounted in `src/app/_layout.tsx`) uses `AppState.addEventListener('change', state => ...)` on transition to `'active'`. The recommendations hook can use the same `AppState` pattern to refresh stale weather/recommendations on foreground resume.
- **H3 on frontend**: Both `h3-js` (`^4.5.0`) and `h3` (`^2.0.8`) are already in `frontend/package.json`.
- **Frontend test runner**: `package.json` uses Node's built-in test runner (`"test": "node --test tests/*.test.mjs"`). Tests (`tests/map.test.mjs`, `tests/registration.test.mjs`) import TypeScript modules directly and mock `globalThis.fetch`.
  - *Note*: Jest, `jest-expo`, and `@testing-library/react-native` are **not** installed in `frontend/package.json`.

---

## 2. Backend Discovery (`services/`)

### 2.1 Application API (`services/application-api`)
- **Stack**: Spring Boot `4.1.2`, Java `25`, Maven (`./mvnw` / `mvnw.cmd`).
- **Base package**: `services.applicationapi`.
- **Package conventions**: Feature packages (`login`, `register`, `tripplan`, `airport`, `country`, `municipality`, `emergencynumber`, `dangerouszone`, `session`, `user`) use subpackages:
  - `configurations` (`@Configuration` + `@EnableConfigurationProperties` and `@ConfigurationProperties("app.<feature>")` records)
  - `controllers` and `controllers.exceptionhandlers`
  - `dto` (Java `record`s with Jakarta Validation annotations)
  - `model` (JPA entities)
  - `repository` (Spring Data JPA repositories)
  - `service` (`XService` interface + `XServiceImpl` implementation)
- **Exception handling**: Scoped `@RestControllerAdvice(assignableTypes = ...)` returning `ResponseEntity<ProblemDetail>` (`ProblemDetail.forStatusAndDetail(status, detail)`).
- **Security (`SecurityConfiguration.java`)**:
  - Stateless OAuth2 Resource Server using `@Qualifier("applicationJwtDecoder") JwtDecoder`.
  - Public routes explicitly whitelisted via `.permitAll()`:
    - `POST /api/registrations/google`
    - `POST /api/sessions/**` and `/api/mobile/sessions/**`
    - `GET /actuator/**`
    - `GET /api/v1/dangerous-zones`
  - `.anyRequest().authenticated()` applies to everything else.
  - **Auth decision for recommendations**: Whitelisting `GET /api/v1/recommendations/activities` with `.permitAll()` in `SecurityConfiguration` (matching `GET /api/v1/dangerous-zones` and the public `/map` screen) allows both guest and signed-in users to receive weather-based activity recommendations on app open.
- **HTTP client convention**: Spring `RestClient` backed by `JdkClientHttpRequestFactory` (`java.net.http.HttpClient`) with explicit connect and read timeouts from `@ConfigurationProperties` (e.g., `FlightSearchConfiguration.java`, `SafetyInformationConfiguration.java`).

### 2.2 Routing Service (`services/routing-service`)
- **Base package**: `services.routingservice`.
- **Current scope**: Only contains `dangerouszone` reporting and heatmap aggregation (`DangerousZoneController`, `DangerousZoneService`, `DangerousZoneRepository`).
- **H3 usage**: Uses `com.uber:h3:4.1.1` (`H3Core.newInstance()`) at **resolution 11** (`h3.latLngToCellAddress(request.lat(), request.lng(), 11)`) for dangerous zone cells.
- **POI / PostGIS presence**: None. Routing Service does not connect to the `map-data` PostGIS database and holds no POI queries.

### 2.3 Decision: Home of the Recommendation Logic
- **Decision**: Implement the feature in **Application API** (`services/application-api`) under package `services.applicationapi.recommendation`.
- **Reasoning**:
  1. Application API owns all user-facing discovery endpoints (`/api/trip-plans/**`, `/api/v1/dangerous-zones`) and is the sole backend URL configured in the frontend (`EXPO_PUBLIC_API_BASE_URL`).
  2. Application API owns the primary PostgreSQL database and Flyway migrations for application configuration/rules.
  3. Routing Service only handles hazard reporting (`dangerous_zones`) and has no POI or map-data database connection.

---

## 3. Database and Migrations

### 3.1 Existing Flyway Migrations
- **`services/application-api/src/main/resources/db/migration/`**:
  1. `V1__create_users_and_external_identities.sql` (`users`, `user_identities`)
  2. `V2__create_refresh_sessions.sql` (`refresh_sessions`)
  3. `V3__create_countries_municipalities_and_airports.sql` (`countries`, `municipalities`, `airports`)
  4. `V4__create_trip_plans.sql` (`trip_plans`)
  5. `V5__create_emergency_numbers.sql` (`emergency_numbers`)
  - **Latest version**: `V5`.
  - **Next version to create**: `V6__create_activity_recommendation_tables.sql` (and `V7__seed_activity_recommendation_rules.sql` or seeded within `V6`).
- **`services/routing-service/src/main/resources/db/migration/`**:
  - `V1__create_dangerous_zones.sql` (`dangerous_zones`).
- **`services/map-data/database/migrations/`**:
  - `V1__basemap_tiles.sql` (`tiles.basemap` function and `features_minzoom_idx` on `osm.features`).

### 3.2 Schema Conventions
- Plural `snake_case` table names (`activity_categories`, `weather_activity_rules`), `CHECK` constraints for enum-like columns, `TIMESTAMPTZ` for timestamps, explicit constraint and index naming (`<table>_<col>_idx`, `<table>_<col>_unique`).
- Neither `weather_activity_rule` nor `activity_category` exists yet.

---

## 4. Map Data / OSM POI Sourcing (`services/map-data/`)

### 4.1 PostGIS Schema and Import Pipeline
- **Stack**: Separate Docker Compose project (`name: walkaround-map` in `services/map-data/compose.yaml`) running `postgis/postgis:17-3.5`.
- **Import tool**: `osm2pgsql` with flex output (`services/map-data/importer/flex.lua`).
- **Table schema (`osm.features`)**:
  - `osm_id` (`bigint`), `osm_type` (`char(1)`)
  - `kind` (`text NOT NULL`) — `'poi'`, `'landcover'`, `'water'`, `'building'`, `'place'`, `'road'`, `'waterway'`, `'coastline'`, `'boundary'`
  - `class` (`text NOT NULL`)
  - `name` (`text`, populated from `tags['name:ro'] or tags.name`)
  - `minzoom` (`int NOT NULL`)
  - `geom` (`geometry(Geometry, 3857) NOT NULL`) — **SRID 3857 (Web Mercator)**
- **Indexes**:
  - GiST spatial index on `osm.features(geom)` created by `osm2pgsql`.
  - B-tree index `features_minzoom_idx` on `osm.features(minzoom)`.
- **Database roles (`services/map-data/database/init.sh`)**:
  - `map_import`: owns schemas `osm`, `osm_middle`, `tiles`.
  - `map_tiles`: read-only user (`default_transaction_read_only = on`, `statement_timeout = '15s'`) with `SELECT` privilege on `osm.features`.
- **Geographic coverage**:
  - Geofabrik Romania extract (`https://download.geofabrik.de/europe/romania-latest.osm.pbf`), bounds `[19.5, 43.4, 30.0, 48.5]` (covering Bucharest, Cluj-Napoca, Brașov, Constanța, etc.).

### 4.2 How Candidate OSM Tags Map to `osm.features`
Because `flex.lua` flattens tags into `(kind, class, name)` rather than storing `hstore`/`jsonb`:
1. **POIs (`kind = 'poi'`)**: `flex.lua` sets `poi = tags.amenity or tags.tourism or tags.shop` and inserts a point (or area centroid) with `kind = 'poi', class = poi, minzoom = 15` whenever `tags.name` is present.
   - Available in `osm.features` with `kind = 'poi'`:
     - `class = 'museum'` (`tourism=museum`)
     - `class = 'gallery'` (`tourism=gallery`)
     - `class = 'viewpoint'` (`tourism=viewpoint`)
     - `class = 'cafe'` (`amenity=cafe`)
     - `class = 'library'` (`amenity=library`)
     - `class = 'cinema'` (`amenity=cinema`)
     - `class = 'theatre'` (`amenity=theatre`)
     - `class = 'restaurant'` (`amenity=restaurant`)
     - `class = 'ice_cream'` (`amenity=ice_cream`)
     - `class = 'marketplace'` (`amenity=marketplace`)
2. **Parks & Gardens (`kind = 'landcover'`)**: `flex.lua` inserts closed ways/multipolygons with `leisure = 'park'` or `leisure = 'garden'` as `kind = 'landcover'`, `class = 'park' | 'garden'` (with Polygon/MultiPolygon `geom` in SRID 3857).
3. **Water bodies (`kind = 'water'`)**: lakes/reservoirs/water bodies are stored with `kind = 'water'`.
4. **Gaps in current `flex.lua`**:
   - `leisure=playground|swimming_pool|sports_centre` and `natural=beach` are currently **not** extracted by `flex.lua` unless the object also carries an `amenity`, `tourism`, or `shop` tag.
   - Extra metadata tags (`opening_hours`, `addr:*`) are not persisted in `osm.features` (so `openingHours` and `address` in `ActivityRecommendationDto` will be `null` unless `flex.lua` is extended and re-imported).

### 4.3 Decision: POI Access Strategy & Weather Fetch Placement
- **POI Access Strategy — Option A (Second read-only `DataSource` in Application API)**:
  - Configure an optional/dedicated read-only `DataSource` (`mapDataSource` / `mapJdbcTemplate`) in `services/application-api` using the `map_tiles` read-only role credentials (`app.recommendation.poi-datasource.*` bound to `MAP_DATASOURCE_URL`, `MAP_DATASOURCE_USERNAME`, `MAP_DATASOURCE_PASSWORD`), kept completely separate from the primary `@Primary` JPA/Flyway `DataSource`.
  - Expose `map-db` on loopback in `services/map-data/compose.yaml` (e.g. `127.0.0.1:${MAP_DB_PORT:-5435}:5432`) so `application-api` running on the host can query it directly.
  - Query `osm.features` using a two-stage spatial filter that leverages the existing **SRID 3857 GiST index** while computing exact spheroidal distance in meters:
    ```sql
    WITH origin AS (
        SELECT
            ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS pt_4326,
            ST_Transform(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326), 3857) AS pt_3857
    )
    SELECT
        f.osm_type || ':' || f.osm_id AS id,
        f.kind,
        f.class AS osm_class,
        f.name,
        ST_Y(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS latitude,
        ST_X(ST_Transform(ST_PointOnSurface(f.geom), 4326)) AS longitude,
        ST_Distance(
            ST_Transform(ST_PointOnSurface(f.geom), 4326)::geography,
            origin.pt_4326::geography
        ) AS distance_meters
    FROM osm.features f
    CROSS JOIN origin
    WHERE f.geom && ST_Expand(origin.pt_3857, :mercatorRadius)
      AND ST_DWithin(
          ST_Transform(ST_PointOnSurface(f.geom), 4326)::geography,
          origin.pt_4326::geography,
          :radiusMeters
      )
      AND f.name IS NOT NULL
      AND BTRIM(f.name) <> ''
      AND (
          (f.kind = 'poi' AND f.class = ANY(:poiClasses))
          OR (f.kind = 'landcover' AND f.class = ANY(:landcoverClasses))
          OR (f.kind = 'water' AND f.class = ANY(:waterClasses))
      )
    ORDER BY distance_meters ASC
    LIMIT :limit
    ```
  - Skip the optional `poi_recommendation_cache` table in Postgres (YAGNI), as `osm.features` has a GiST index on `geom` and `ActivityRuleService` caches rules in memory.
- **Weather Fetch Placement — Default (Frontend Hook Calls Open-Meteo Directly)**:
  - The frontend hook `useOpenMeteoWeather` fetches `https://api.open-meteo.com/v1/forecast` directly (with 5s timeout, 1 retry on network/5xx, 10–15 min in-memory cache keyed by rounded coordinates, and immediate-future 3h degradation rule) and sends the `WeatherSnapshot` via query params to `GET /api/v1/recommendations/activities`.
  - Note on Open-Meteo licensing: The free tier (`api.open-meteo.com`) is for non-commercial use only; commercial deployment requires an Open-Meteo commercial plan or self-hosted instance.

---

## 5. Risks and Mitigations

1. **Docker Engine Availability & Local Dev without PostGIS (`map-db`)**:
   - *Risk*: `services/map-data` requires Docker and a completed Romania import (~hundreds of MB), which may not always be running during local development.
   - *Mitigation*: If the `map-db` connection fails or is unconfigured, `PoiRepository` / `PoiLookupService` should catch data-access errors and degrade cleanly (or return an empty list / structured unavailable status) rather than crashing `application-api` at startup. Primary Flyway and JPA remain strictly bound to the `@Primary` Application API datasource.
2. **Limited OSM Tag Coverage in `flex.lua`**:
   - *Risk*: `osm.features` only stores `(kind, class, name)` from `amenity`, `tourism`, `shop`, `leisure=park|garden`, and `water`. Tags like `leisure=swimming_pool|sports_centre` or `natural=beach` are not in `osm.features` unless `flex.lua` is updated.
   - *Mitigation*: Map the seeded `activity_category` definitions to the classes present in `osm.features` (`park`, `garden`, `viewpoint`, `cafe`, `ice_cream`, `marketplace`, `museum`, `gallery`, `library`, `cinema`, `theatre`, `restaurant`, `water`), and optionally extend `flex.lua` to also index `leisure` (`swimming_pool`, `sports_centre`, `playground`) and `natural=beach` as `poi` features for future imports.
3. **Frontend Test Runner (`node --test` vs Jest/RNTL)**:
   - *Risk*: Phase 5 mentions `renderHook` / React Native Testing Library, whereas `frontend/package.json` uses `node --test tests/*.test.mjs` with zero Jest/RNTL dependencies.
   - *Mitigation*: Write pure unit and state-machine/hook logic tests under `frontend/tests/*.test.mjs` using `node:test` so `npm test` runs fast and dependency-free, or confirm before Phase 5 if adding Jest/RNTL is desired.
