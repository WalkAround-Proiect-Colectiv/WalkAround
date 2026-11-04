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
- Provides pedestrian-optimized route calculation (publicly accessible).
- Receives start and end coordinates and acts as a client to the local Valhalla Docker container to compute paths specifically mapped for walkways and pedestrian infrastructure.

Security is configured to use stateless JWT authentication. Fetching routes and heatmaps is publicly accessible, while reporting a zone requires authentication.

## Local Environment & Valhalla Setup

Because map tiles are massive, the local `valhalla_custom_data/` folder is ignored in Git. However, the Docker configuration handles everything automatically.

To initialize the Valhalla routing engine locally:

1. Start the Valhalla container from the root of the project (where `docker-compose.yaml` is):
```sh
docker-compose up -d valhalla
```

Wait for the build process: On its first run, the container will automatically create the valhalla_custom_data/ folder, download the OpenStreetMap data for Romania, and build the routing tiles. (Note: This building process may take several minutes depending on your CPU).

Verify Valhalla is ready by checking its logs (wait until it starts listening on port 8003):

```sh
docker logs -f valhalla
```

## Run and Test
Use Java 25. Ensure your PostgreSQL databases (application-api-db and routing-api-db), local Kafka broker, and Valhalla engine are running (via the root docker-compose.yaml).

Don't forget to configure your local .db.env and .env files based on the .example files provided.

From `services/routing-service/`:

```
./mvnw spring-boot:run
./mvnw test
```