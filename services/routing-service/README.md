# Routing Service

This is a Spring Boot service responsible for managing geographical routing data, calculating pedestrian paths, and handling the dangerous zones feature. It leverages Uber's H3 library for hexagonal hierarchical spatial indexing and a self-hosted Valhalla routing engine.

## Current State

The service exposes secure REST APIs to manage pedestrian mobility and safety:

**1. Dangerous Zones**
* Fetches dangerous zones heatmaps (publicly accessible).
* Allows users to report new dangerous zones (requires valid user session).
* Calculates the corresponding H3 index based on latitude and longitude.
* Persists the report in the PostgreSQL database (`dangerous_zones` table).
* Produces a real-time event to the `dangerous-zones-topic` via Kafka to be processed by downstream services.

**2. Route Calculation**
* Provides pedestrian-optimized route calculation (publicly accessible).
* Receives start and end coordinates and acts as a client to the local Valhalla Docker container to compute paths specifically mapped for walkways and pedestrian infrastructure.

Security is configured to use stateless JWT authentication. Fetching routes and heatmaps is publicly accessible, while reporting a zone requires authentication.

## Run and Test

Use Java 25. Ensure your PostgreSQL database, local Kafka broker, and Valhalla engine are running (via the root `docker-compose.yaml`).

From `services/routing-service/`:

```sh
./mvnw spring-boot:run
./mvnw test