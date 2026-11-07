# Realtime Service

This is a separate Spring Boot service dedicated to hosting real-time functionality and live data broadcasting. It acts as a bridge between the backend event streams and the mobile frontend.

## Current State

The service currently implements the real-time broadcasting flow for the dangerous zones feature. Its primary responsibilities are:
1. **Kafka Consumer**: Listens to the `dangerous-zones-topic` for newly reported zones.
2. **WebSocket Broadcaster**: Pushes these live updates to all actively connected clients via a raw WebSocket endpoint (`/ws/dangerous-zones`).

### Event reminders (SCRUM-35)

The service also delivers per-user event reminders:
1. **Kafka Consumer**: Listens to `event-reminders-topic` (setting `EVENTS_REMINDER_TOPIC`), where the Application API publishes reminders that are due. Each message is JSON with `type: "EVENT_REMINDER"`, `userId`, `eventKey`, `localityKey`, `title`, `startsAt`, `allDay` and `venueName`. This listener uses its own consumer group, `${CONSUMER_GROUP_ID}-notifications`.
2. **WebSocket endpoint**: `/ws/notifications?token=<access token>`. Unlike `/ws/dangerous-zones`, a connection is accepted only with a valid WalkAround access token, checked with the same signing secret, issuer, audience and `token_use` claim as the Application API. The token is a query parameter because browsers cannot send headers with WebSockets. Each reminder is sent only to the connections of the user named in the message; a user can be connected from several devices.

This requires the same `JWT_SIGNING_SECRET`, `JWT_ISSUER` and `JWT_AUDIENCE` as the Application API (see `.env.example`). The notification code is in `services.realtimeservice.notifications` and is registered separately from the dangerous-zone `WebSocketConfig`. Reminders are not stored here: users who are offline get missed reminders from the Application API when the app opens.

Security is configured to allow open WebSocket handshakes for seamless frontend connectivity. This service operates completely statelessly and relies entirely on the Kafka broker for data input, with no direct database persistence (PostgreSQL is managed by the application and routing APIs).

## Run and Test

Use Java 25. Ensure your local Kafka broker is running via the root `docker-compose.yaml` before starting this service.

From `services/realtime-service/`:

```sh
./mvnw spring-boot:run
./mvnw test
```

`NotificationsTest` covers token checks and per-user delivery without Kafka or Docker.