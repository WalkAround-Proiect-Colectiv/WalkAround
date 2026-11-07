# Upcoming events (SCRUM-35)

A dedicated section listing upcoming events in the town the user is in, with marking and in-app reminders. Signed-out visitors are redirected to `/login`. The backend is described in the [events backend README](../../../services/application-api/src/main/java/services/applicationapi/events/README.md).

## Routes

| Route | File | Purpose |
| --- | --- | --- |
| `/events` | `pages/EventsScreen.tsx` | **Nearby** and **Interested** tabs. Opened from **Upcoming events** in the map's features menu. |
| `/events/details?localityKey=…&eventKey=…` | `pages/EventDetailsScreen.tsx` | One event: date, venue, address, description, ticket links, the interested toggle and reminder options. Map features can open events here with the same two keys. |

## Position

The list asks for foreground location permission and uses the device position. If permission is denied or location is unavailable, it uses the map centre passed by the menu as `lat`/`lng` route parameters and says so under the town name. Without either, it asks the user to allow location. The backend rounds the position to about 1 km before resolving the town.

## Behaviour

- The nearby list loads once when the screen opens; **Try again** reloads after an error. The first request for a town can take several seconds because the backend may search the provider; later requests come from its cache.
- The list shows whatever the backend returns for the town. An empty result shows "No upcoming events near you". A `stale` response shows a note that an older saved list is displayed.
- The heart on a card marks or unmarks an event without a reminder. The **Interested** tab reloads when the screen regains focus, so changes made on the details screen appear.
- Reminder options are Off, 1 hour, 1 day and 1 week before. A reminder whose time has already passed is sent right away.
- Ticket and image links are used only when they are `https://` URLs (`isSafeLink` in `format.ts`).
- The data attribution from the backend is shown under the list.

## Reminders

`components/ReminderBanner.tsx` is mounted in the root layout, so reminders appear over any screen while the app is open.

- It connects to `${EXPO_PUBLIC_WS_URL}/ws/notifications?token=<access token>`. Browsers cannot send headers with WebSockets, so the token goes in the query string. The connection is reopened when the session token is refreshed or the connection drops; an expiring session is refreshed before connecting.
- On sign-in or app start it loads `GET /api/events/reminders/unseen`, so reminders sent while the app was closed still appear.
- **Dismiss** and **View event** mark the reminder as seen. **View event** opens the details screen.

There are no operating-system push notifications; reminders are in-app only.

## Files

- `api/events.ts`: request types and calls. Uses a 60-second timeout because the first search for a town can be slow.
- `format.ts`: date formatting, reminder labels and the https link check.
- `components/EventCard.tsx`: a list card. The open area and the heart are sibling buttons, since a button inside a button is invalid on web.

## Tests

`tests/events.test.mjs` covers reminder options, date formatting and link checks. Run `npm test`.
