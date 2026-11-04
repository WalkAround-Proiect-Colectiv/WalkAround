# Realtime Service

This is a separate Spring Boot service dedicated to hosting real-time functionality and live data broadcasting. It acts as a bridge between the backend event streams and the mobile frontend.

## Current State

The service currently implements the real-time broadcasting flow for the dangerous zones feature. Its primary responsibilities are:
1. **Kafka Consumer**: Listens to the `dangerous-zones-topic` for newly reported zones.
2. **WebSocket Broadcaster**: Pushes these live updates to all actively connected clients via a raw WebSocket endpoint (`/ws/dangerous-zones`).

Security is configured to allow open WebSocket handshakes for seamless frontend connectivity. This service operates completely statelessly and relies entirely on the Kafka broker for data input, with no direct database persistence (PostgreSQL is managed by the application and routing APIs).

## Run and Test

Use Java 25. Ensure your local Kafka broker is running via the root `docker-compose.yaml` before starting this service.

From `services/realtime-service/`:

```sh
./mvnw spring-boot:run
./mvnw test