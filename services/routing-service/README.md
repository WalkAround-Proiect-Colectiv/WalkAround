# Routing Service

This is a Spring Boot service responsible for managing geographical routing data, calculating pedestrian paths, and handling the dangerous zones feature. It leverages Uber's H3 library for hexagonal hierarchical spatial indexing and a self-hosted Valhalla routing engine.

## Current State

The service exposes secure REST APIs to manage pedestrian mobility and safety:

**1. Dangerous Zones**
- Fetches dangerous zones heatmaps (publicly accessible).
- Allows users to report new dangerous zones (requires valid user session).
- Calculates the corresponding H3 index based on latitude and longitude.
- Persists the report in the PostgreSQL database (`dangerous_zones` table).
- Produces a real-time event to the `dangerous-zones-topic` via Kafka to be processed by downstream services.

**2. Route Calculation**
- Provides pedestrian-optimized route calculation (publicly accessible) via `POST /api/v1/routes/pedestrian`.
- Receives start and end coordinates plus an optional ordered list of intermediate stops. Routes with and without stops use the same endpoint.
- Builds the Valhalla `locations` list as start → stops (in the order received) → end, and calls the local Valhalla container with `pedestrian` costing. Valhalla returns a single route covering all points, with one leg per segment.

Example request body:

    {
      "startLat": 44.4268,
      "startLng": 26.1025,
      "endLat": 44.4378,
      "endLng": 26.0969,
      "stops": [
        { "lat": 44.4323, "lng": 26.1063 }
      ]
    }

`stops` may be an empty array for a direct route.

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
