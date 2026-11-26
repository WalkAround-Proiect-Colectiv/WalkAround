# Notification inbox

The `notifications` feature contains the authenticated API client, the inbox page, and the deep-link registry. `app/notifications.tsx` exposes the page; the map's floating menu opens it. The page reloads on focus, on an `INBOX_CHANGED` WebSocket signal, and after the notification socket reconnects. It shows unread state, pages through older rows, marks a row read, and opens a supported target. `liveInbox.ts` is a small in-app event bus; the existing global `ReminderBanner` owns the authenticated notification socket and publishes only the refresh signal to the inbox page. The inbox then fetches authoritative data over HTTP.

Targets are data from the server, never executable URLs. `navigation.ts` accepts only known route keys and validates parameters before calling Expo Router. Add a registry branch when a new feature wants its inbox notifications to open a resource. If a target key is unknown, the page shows a helpful message and does not navigate. The backend writer and storage do not need to change.

The app's temporary WebSocket `ReminderBanner` remains separate. An inbox item appears only after a feature deliberately writes it through the backend `NotificationInboxWriter`; live delivery does not automatically persist anything.
