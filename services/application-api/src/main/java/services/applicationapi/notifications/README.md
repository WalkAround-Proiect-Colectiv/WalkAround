# Notification inbox

This package owns durable, user-relevant notifications across the application. It is deliberately separate from live WebSocket banners. A feature decides whether an event merits inbox storage and calls `NotificationInboxWriter.publish(NotificationDraft)`; the inbox does not subscribe to every real-time event and has no dependency on trip planning, events, or chat.

## Publishing contract

`NotificationDraft` requires a recipient user ID, a stable `source` and `sourceEventId` pair, a category, title, body, and an optional `NotificationTarget`. Use the same `sourceEventId` on retries. The unique database constraint on `(recipient_user_id, source, source_event_id)` makes repeated calls idempotent. Do not include secrets or sensitive content in the title, body, or target parameters. The writer joins an existing Spring transaction; if a feature calls it while saving a business object, both changes commit or roll back together. A feature can call it outside a business transaction and the writer creates its own transaction.

For example, a feature could publish a draft with `source = "trip_plans"`, `sourceEventId = "plan-saved:" + planId`, `category = "TRIP"`, and target `NotificationTarget("group_plan", Map.of("groupId", groupId, "planId", planId))`. This example is a contract, not an automatic integration. A live event alone does not create an inbox row.

## Deep links

The stored target is a route **key** and string parameter map, never an absolute URL or arbitrary path. The frontend's `notifications/navigation.ts` registry validates parameters and maps known keys to internal Expo Router routes. Unknown keys remain visible in the inbox but cannot navigate until the client supports them. Currently registered keys are `trip_group` (`groupId`), `group_plan` (`groupId`, `planId`), and `event` (`eventKey`, `localityKey`). Adding a feature destination requires a new frontend registry entry, without changing the backend inbox schema or writer interface.

## API and storage

`GET /api/notifications?before=<notification UUID>&limit=20` returns newest first with `items` and `nextCursor`. Limit must be 1–50. `GET /api/notifications/unread-count` returns `{ "count": n }`. `PUT /api/notifications/{id}/read` is idempotent and returns 204; a missing or other user's notification returns 404. `DELETE /api/notifications/{id}` hides one of the user's notifications and returns 204, including when the ID is already deleted or does not belong to the user. All requests require the application's JWT; every SQL read and update is scoped to its subject user ID. Responses are not cached. Errors use client-safe `ProblemDetail` bodies.

An item has the following response shape (the IDs and times are illustrative):

```json
{
  "id": "3c09be13-2d1b-4cae-9954-73b93aee456c",
  "category": "TRIP",
  "title": "A group plan was saved",
  "body": "Open the plan to review it.",
  "target": {
    "key": "group_plan",
    "params": {
      "groupId": "20ee72c3-e507-40f4-9f13-e21d43a4a317",
      "planId": "719d1ed3-8362-4689-afcb-75b413d3339b"
    }
  },
  "createdAt": "2026-11-26T10:00:00Z",
  "readAt": null
}
```

Flyway V21 creates `notification_inbox`, its user foreign key, deduplication constraint, chronological listing index, and partial unread index. `read_at` records read state; there is no inbox copy of every WebSocket delivery. The repository uses PostgreSQL JSONB for target parameters and keyset pagination by `(created_at, id)`.

Flyway V23 adds `deleted_at` and indexes for active rows. Deletion is a soft delete: list and unread count exclude deleted rows, while the unique `(recipient_user_id, source, source_event_id)` key remains. This prevents a retried feature event from recreating a notification that the user removed. The pagination cursor can still point to a deleted row, so loading the next page continues correctly after an item is removed.

## Live inbox updates

Flyway V22 creates `notification_inbox_outbox`. When the writer inserts a new inbox row, it inserts an outbox row in the same transaction. Duplicate feature retries create neither a second inbox row nor another signal. `KafkaNotificationInboxOutboxRelay` polls unpublished rows, locks them with `FOR UPDATE SKIP LOCKED`, publishes a small `INBOX_CHANGED` Kafka message keyed by recipient user ID, waits for broker acknowledgement, and marks the outbox row published. Failed sends are retried with bounded backoff. This is at-least-once signal delivery: a crash between Kafka acknowledgement and the database commit can replay the signal, and clients simply refresh the authoritative HTTP inbox. The signal carries only `type`, `userId`, and `notificationId`, not notification text or target data.

The realtime service forwards the signal through the authenticated `/ws/notifications` connection. The frontend refreshes the inbox when it receives the signal and again when the socket reconnects, so messages saved while the client was offline are recovered. WebSocket traffic remains a hint to refresh; only the database determines what the inbox contains. The Application API creates the configurable Kafka topic on startup. Topic, partition count, poll interval, batch size, publish timeout, and retry delay are configured under `app.notifications.inbox` and the matching `INBOX_*` environment variables.

For a manual demo, `scripts/notifications_inbox/seed_notifications.py` writes sample rows and outbox signals for an explicitly supplied user ID. It is not part of startup or production migrations.
