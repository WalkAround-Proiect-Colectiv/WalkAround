# Authenticated notifications

`NotificationWebSocketConfig` exposes `/ws/notifications`. `NotificationHandshakeInterceptor` validates the WalkAround access token with the configured issuer, audience, token use, and signing secret. `NotificationSocketHandler` keeps connections by authenticated user ID and uses a safe concurrent-session wrapper when sending. A user can have several connected devices.

The handler consumes event reminders from `EVENTS_REMINDER_TOPIC` and saved group-plan messages from `GROUP_PLAN_NOTIFICATION_TOPIC` (default `trip-group-plan-notifications-topic`). Both topics carry JSON with a recipient `userId`; messages go only to that user's connections. Group-plan messages also have `type: GROUP_PLAN_SAVED`, `groupId`, `planId`, `groupName`, `title`, and `createdAt`. The Application API creates one message for every member of the group after the plan save commits. Invalid group-plan messages are ignored and logged.

This service does not store notifications. A member who is disconnected when a group-plan message arrives will see the saved plan in the group view later but will not receive the live banner. Event reminders have their own missed-reminder lookup in the Application API.
