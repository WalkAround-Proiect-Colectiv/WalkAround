# WalkAround

WalkAround is a cross-platform Expo app backed by two independent Spring Boot services. The implemented product flow currently covers a public Romania map prototype using hosted vector tiles, Google-based account registration, sign-in, and session restoration.

## Architecture

WalkAround follows a microservices architecture: the client communicates with independently runnable backend services, each responsible for a distinct area of functionality. The current client is built with React Native/Expo and TypeScript; backend services use Spring Boot and Java. The intended data stack is PostgreSQL/PostGIS with Flyway-managed schema changes. The current Application API uses PostgreSQL and Flyway; PostGIS is not yet represented in its migrations. Valhalla is the planned routing engine, but it is not currently integrated.

```mermaid
flowchart LR
	User --> App["Expo app<br/>iOS / Android / Web"]
	App --> Google["Google Identity"]
	App --> API["Application API<br/>Spring Boot"]
	API --> DB[("PostgreSQL")]
	App -. future realtime .-> Realtime["Realtime service<br/>Spring Boot"]
	API -. dependency present .-> Kafka
	Realtime -. dependency present .-> Kafka
```

- `frontend/` contains the Expo Router application and platform-specific session transports.
- `services/application-api/` owns registration, login, JWT validation, user lookup, and refresh-session persistence.
- `services/realtime-service/` is a separate Spring Boot service with WebSocket and Kafka dependencies. It currently has no message handlers or Kafka producers/consumers.
- `docker-compose.yaml` starts PostgreSQL and Kafka only. The frontend and Java services are run separately.

The frontend talks directly to the Application API. PostgreSQL stores user profiles, Google identities, and refresh-session hashes. Kafka and the Realtime service are infrastructure for future functionality; they are not currently part of the registration/login request path.

### Map Direction

The current `/map` prototype uses MapLibre with hosted OpenFreeMap vector tiles and does not require the backend or Google sign-in. See the [frontend guide](frontend/README.md#map-prototype) for setup and controls. The selected long-term architecture is self-hosted tiles:

```mermaid
flowchart LR
	OSM["OSM Romania extract"] --> Import["osm2pgsql import/update"]
	Import --> GeoDB[("PostGIS")]
	GeoDB --> Martin["Martin vector tiles"]
	Martin --> Map["MapLibre renderer"]
	OSM --> Graph["Valhalla graph build/update"]
	Graph --> Routing["Valhalla routes"]
	Routing --> Map
	Reports["Future H3 danger reports"] --> GeoDB
```

This diagram is the target architecture, not deployed infrastructure. Martin needs a suitable tile schema/query layer; importing OSM alone does not produce a finished basemap style. Valhalla's routing graph is built separately from OSM and needs its own update process; the weekly PostGIS import does not automatically update routes. Styles, glyphs, and sprites are also needed for the finished map. Offline packs and terrain elevation are deferred to future stories; neither is included in this prototype.

## Product and Engineering Guardrails

- Start with map coverage for Romania to keep initial map data, storage, and memory requirements manageable.
- Design country coverage to be data-driven where practical, so adding neighboring countries is primarily a matter of supplying map/routing data rather than adding country-specific application logic. Revisit this assumption when selecting data sources and implementing routing.
- Use OpenStreetMap (OSM) as the primary source for map structure and features such as POIs, streets, and roads. The intended update pipeline imports OSM data into PostgreSQL/PostGIS with `osm2pgsql` weekly; plan refreshes so updates are repeatable and do not require reloading map data onto client devices. Include required OSM attribution.
- Use H3 cells to represent reported dangerous areas and their coverage instead of ad hoc circles. Select the cell resolution and aggregation rules to avoid misleading gaps or overly broad affected regions.
- Use Martin as the planned tile server to serve map tiles from the geospatial data store, and MapLibre GL in the client to render them. The tile server supplies viewport-specific data; MapLibre GL is the renderer, not the tile server. Avoid loading the entire regional map dataset into client memory.
- Valhalla is the planned routing engine. Model report severity so lower-risk areas can produce warnings while more serious areas influence route selection; validate the appropriate Valhalla costing or integration approach before defining the routing API. GraphHopper has also been suggested for its user-weighted hazard routing; revisit it only if the team decides to reconsider the current Valhalla choice.
- Discuss complex features and cross-service changes with the team before implementation.

## Local Development

Prerequisites: Node.js/npm, Java 25, and Docker Compose. Google OAuth credentials and application configuration are needed for end-to-end authentication.

1. Copy `.env.example` to `.env` and replace its placeholders. Create the database and Kafka env files referenced by `APPLICATION_API_DB_ENV_FILE_LOCATION` and `KAFKA_ENV_FILE_LOCATION`.
2. Start local infrastructure with `docker compose up -d` from the repository root.
3. Configure and run the Application API as described in [services/application-api/README.md](services/application-api/README.md).
4. Configure and run the app as described in [frontend/README.md](frontend/README.md).

The root `.env.example` contains placeholders rather than ready-to-run values. Frontend configuration is separate and uses `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`, and `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID`.

## Documentation

- [Frontend](frontend/README.md)
- [Application API](services/application-api/README.md)
- [Realtime service](services/realtime-service/README.md)