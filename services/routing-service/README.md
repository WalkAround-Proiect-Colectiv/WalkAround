# Routing Service

This is a Spring Boot service responsible for managing geographical routing data and handling the dangerous zones feature for pedestrians. It leverages Uber's H3 library for hexagonal hierarchical spatial indexing.

## Current State

The service exposes a secure REST API to fetch dangerous zones heatmaps and report new dangerous zones. When a user reports a new zone, the service:
1. Calculates the corresponding H3 index based on latitude and longitude.
2. Persists the report in the PostgreSQL database (`dangerous_zones` table).
3. Produces a real-time event to the `dangerous-zones-topic` via Kafka to be processed by downstream services.

Security is configured to use stateless JWT authentication. Fetching the heatmap is publicly accessible, while reporting a zone requires a valid user session.

## Run and Test

Use Java 25. Ensure your PostgreSQL database and local Kafka broker are running (via the root `docker-compose.yaml`).

From `services/routing-service/`:

```sh
./mvnw spring-boot:run
./mvnw test