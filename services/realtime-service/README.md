# Realtime Service

This is a separate Spring Boot service intended to host realtime functionality. Its dependencies include Spring WebSocket and Kafka, and its security configuration is stateless and requires authentication for non-actuator routes.

## Current State

The source currently contains the Spring Boot application entry point and security configuration, but no WebSocket message mappings, STOMP broker configuration, Kafka listeners, or Kafka producers. Treat this service as a scaffold: there is not yet a realtime API or message flow for the frontend to use.

The root `docker-compose.yaml` provides a local Kafka broker. PostgreSQL is currently owned by the Application API; the realtime service has no persistence integration in source.

## Run and Test

Use Java 25. From `services/realtime-service/`:

```sh
./mvnw spring-boot:run
./mvnw test
```

On Windows, use `mvnw.cmd` in place of `./mvnw`. Kafka connectivity and authenticated realtime endpoints will require configuration and implementation before this service can deliver product behavior.
