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

### Group plan notifications

When a group member saves a plan, the Application API publishes one `GROUP_PLAN_SAVED` message per group member after the save commits. The separate Kafka topic is configured by `GROUP_PLAN_NOTIFICATION_TOPIC` in both services (default `trip-group-plan-notifications-topic`). `NotificationSocketHandler` delivers the message through the existing authenticated `/ws/notifications` connection, so all connected members, including the creator, receive an in-app banner with a link to the plan. Group-plan notifications are live only; offline members can open the saved plan from the group view later. See [`notifications/README.md`](src/main/java/services/realtimeservice/notifications/README.md) for the message contract.

### Group chat

`/ws/group-chats?token=...` delivers chat messages to one conversation after validating a short-lived, group-scoped ticket from the Application API. The `GROUP_CHAT_TOPIC` setting must match the Application API topic. Every realtime instance consumes the topic with its own consumer group, while Kafka partitions keep each conversation's events ordered. Clients wait for the `SUBSCRIBED` acknowledgement before requesting missed messages over HTTP. The realtime service does not save chat messages or accept chat writes over WebSocket. See [`chat/README.md`](src/main/java/services/realtimeservice/chat/README.md) for the event and reconnect contract.

The dangerous-zone WebSocket is public; notification and group-chat handshakes require valid scoped tokens. This service operates without database persistence and receives its live data from Kafka.

## Run and Test

Use Java 25. Ensure your local Kafka broker is running via the root `docker-compose.yaml` before starting this service.

From `services/realtime-service/`:

```sh
./mvnw spring-boot:run
./mvnw test
```

`NotificationsTest` covers token checks and per-user delivery without Kafka or Docker.
