# Routing Service

This is a Spring Boot service responsible for managing geographical routing data, calculating pedestrian paths, and handling the dangerous zones feature. It leverages Uber's H3 library for hexagonal hierarchical spatial indexing and a self-hosted Valhalla routing engine.

## Current State

The service exposes secure REST APIs to manage pedestrian mobility and safety:

**1. Dangerous Zones**
*   Fetches dangerous zones heatmaps (publicly accessible).
*   Allows users to report new dangerous zones (requires valid user session).
*   Calculates the corresponding H3 index based on latitude and longitude, and now calculates and saves the cell's center coordinates (`center_lat` and `center_lng`) to allow highly optimized spatial bounding-box database queries.
*   Persists the report in the PostgreSQL database (`dangerous_zones` table).
*   Produces a real-time event to the `dangerous-zones-topic` via Kafka to be processed by downstream services.

**2. Route Calculation**
*   Provides pedestrian-optimized route calculation (publicly accessible) via `POST /api/v1/routes/pedestrian`.
*   Receives start and end coordinates plus an optional ordered list of intermediate stops. Routes with and without stops use the same endpoint.
*   Supports dynamic **Danger Zone Avoidance**: When `avoidDanger` is requested, the service calculates a bounding box (with an added safety buffer) around the route, filters temporally active zones from the database using their center coordinates, and validates in $O(1)$ time that no user waypoint falls directly inside a danger zone.
*   Uses `H3Core` to natively group adjacent dangerous cells into continuous clusters, extracting only their outer perimeters (ignoring internal holes) to pass as `avoid_polygons` directly into Valhalla's routing graph.
*   Builds the Valhalla `locations` list as start → stops (in the order received) → end, and calls the local Valhalla container with `pedestrian` costing. Letting Spring serialize the request avoids locale-dependent decimal formatting in the JSON body. Valhalla returns a single route covering all points, with one leg per segment.
*   Employs RFC 7807 Problem Details to handle routing exceptions gracefully, returning a `422 Unprocessable Entity` with specific reasons (e.g., `POINT_IN_DANGER_ZONE`, `NO_PATH`, or `TOO_MANY_ZONES` if the graph becomes too fragmented for Valhalla to compute).

Example request body:

```json
{
  "start": { "lat": 44.4268, "lng": 26.1025 },
  "end": { "lat": 44.4378, "lng": 26.0969 },
  "stops": [
    { "lat": 44.4323, "lng": 26.1063 }
  ],
  "avoidDanger": true,
  "departureTime": "2026-11-09T22:30:00+02:00"
}
```
`stops` may be an empty array for a direct route. If `departureTime` is omitted, the service falls back to the local system time (Europe/Bucharest) to determine active danger zones.

## Local Environment & Valhalla Setup

Because map tiles are massive, the local `valhalla_custom_data/` folder is ignored in Git. However, the Docker configuration handles everything automatically.

To initialize the Valhalla routing engine locally:

1. Start the Valhalla container from the root of the project (where `docker-compose.yaml` is):
```sh
docker-compose up -d valhalla
```

Wait for the build process: On its first run, the container will automatically create the valhalla_custom_data/ folder, download the OpenStreetMap data for Romania, and build the routing tiles. (Note: This building process may take several minutes depending on your CPU). The Compose service limits Valhalla to `VALHALLA_SERVER_THREADS=2` by default; increasing it can exhaust Docker's open-file limit during server startup.

Verify Valhalla is ready by checking its logs (the container listens on port 8002, mapped to `VALHALLA_HOST_PORT` on the host):

```sh
docker logs -f walkaround-valhalla
```

## Run and Test
Use Java 25. Ensure your PostgreSQL databases (application-api-db and routing-api-db), local Kafka broker, and Valhalla engine are running (via the root docker-compose.yaml).

Don't forget to configure your local .db.env and .env files based on the .example files provided.

Set `VALHALLA_URL` to the Valhalla route endpoint, for example `http://localhost:8022/route` when the root Compose file maps the container's port 8002 to host port 8022. The routing service sends its pedestrian request directly to this URL. A URL ending at the host root sends `POST /` and cannot calculate a route. Restart the routing Spring service after changing its `.env`.

The route service sends a JSON-serialized request to Valhalla. This avoids malformed coordinate values from locale-dependent number formatting. Route-provider HTTP failures are logged by status; the application API proxy returns a generic upstream error instead of claiming every failure is a connection problem. See the [route service package](src/main/java/services/routingservice/routecalculation/service/README.md).

From `services/routing-service/`:

```
./mvnw spring-boot:run
./mvnw test
```
