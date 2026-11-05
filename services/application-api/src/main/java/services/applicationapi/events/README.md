# Upcoming events backend (SCRUM-35)

This feature lists upcoming events in the town the user is in, lets signed-in users mark events they are interested in, and sends in-app reminders before marked events. All endpoints require a signed-in user.

## Where to start

| Concern | Package / class | Main responsibility |
| --- | --- | --- |
| HTTP endpoints | [`controllers/EventsController`](controllers/EventsController.java) | Nearby list, event details, marking, reminders. Errors are mapped in `controllers/exceptionhandlers`. |
| Town lookup | [`service/locality`](service/locality) | Turn coordinates into the containing city, town or commune with OpenStreetMap Nominatim. |
| Event sources | [`service/source`](service/source) | `SerpApiEventSource` (live Google Events) or `RecordedEventSource` (saved responses, no API calls). |
| Cleaning results | `service/EventListBuilder`, `service/EventDateParser` | Parse dates, infer the missing year, drop past events and duplicates, sort by start time. |
| Caching | `service/EventsService` | Memory cache, then a per-town database snapshot, then the event source. |
| Marked events | `service/EventInterestService` | Save a snapshot of a marked event and its reminder setting. |
| Reminders | [`service/reminders`](service/reminders) | Every minute, publish due reminders to Kafka for the realtime service. |
| Settings | [`configurations`](configurations) | `app.events.*` properties, HTTP clients with timeouts, scheduling. |

## Endpoints

| Method and path | Purpose |
| --- | --- |
| `GET /api/events/nearby?lat=&lng=` | Events for the town containing the position. `locality` is null when the position is not in any town; `stale` is true when an older cached list is served because the source failed. |
| `GET /api/events/details?localityKey=&eventKey=` | One event from the cache, plus the user's interest if marked. Used by the details screen and can be opened from the map. |
| `GET /api/events/interests` | The user's marked events that have not passed. |
| `PUT /api/events/interests` | Body `{ localityKey, eventKey, remindBeforeMinutes }`. Marks an event, or changes its reminder. `remindBeforeMinutes` is `null` for no reminder, otherwise 1 to 43200 (30 days). |
| `DELETE /api/events/interests/{eventKey}` | Removes a marked event. |
| `GET /api/events/reminders/unseen` | Reminders already sent but not yet dismissed, for users who were offline. |
| `POST /api/events/interests/{eventKey}/reminder-seen` | Records that the user dismissed or opened a reminder. |

## How a nearby request works

1. The position is rounded to two decimals (about 1 km) and reverse-geocoded with Nominatim. The result is cached in memory.
2. The town's event list is taken from memory if it is younger than `EVENTS_CACHE_TTL` (24 hours by default).
3. Otherwise the `event_locality_snapshots` row for the town is used if it is fresh. This survives restarts.
4. Otherwise the event source is called. For SerpApi this is two searches: `"Events in <town>, <county>"` and `"Events"` with the town as the search location. Testing showed these two wordings return different events, while paging (`start`) and date filters (`htichips`) returned the same results, so no further searches are made. Results are merged, cleaned, saved to the snapshot and returned.
5. If the source fails and an older snapshot exists, it is served with `stale: true`.

Whatever the provider returns for the town is shown. Google widens searches for small towns, so a town such as Florești returns Cluj-Napoca events; each event keeps its real address. Positions outside any town, or towns without results, get an empty list.

Concurrent requests for the same town share one fetch. Past events are also filtered when cached lists are read, so a list fetched in the morning hides events that ended later that day.

## Event keys and dates

The provider has no stable event IDs. An event key is the first 32 hex characters of a SHA-256 of the accent-free title, local start day and venue. The same event from both searches therefore gets the same key, and keys stay stable between refreshes.

List results have dates like `Nov 6, 8:00 PM` or `Feb 11` without a year. The next occurrence of that day (today included) is assumed, because the provider lists only upcoming events. Dates without a time are treated as all-day events. Times are interpreted in `EVENTS_TIME_ZONE` (`Europe/Bucharest`).

## Marked events

`event_interests` stores a copy of the event (title, start, venue, address, image, first ticket link), so the Interested list and reminders work without calling the provider again. No other events are stored per row.

When an event is marked, one SerpApi Event Details request (one search) adds the exact date with its year, the full address and a description. If that request fails, or the provider is `recorded`, the cached list data is saved instead.

## Reminders

`EventReminderJob` runs every minute. It finds interests whose `remind_at` has passed, the event has not started and `reminded_at` is empty. It publishes each one to Kafka (`EVENTS_REMINDER_TOPIC`, default `event-reminders-topic`) with the user id as the key, then sets `reminded_at` so it is sent once. A reminder that cannot be published stays due and is retried on the next run. A reminder whose time has already passed when it is set fires on the next run.

The realtime service delivers the message only to that user's open `/ws/notifications` connections. See the [realtime service README](../../../../../../../realtime-service/README.md).

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `EVENTS_PROVIDER` | `serpapi` | `serpapi` for live data, `recorded` to replay the responses in `src/main/resources/events/recorded/` (Cluj-Napoca, Bucharest, Brașov, Florești; recorded 2026-11-05). Use `recorded` for development and tests. |
| `EVENTS_SERP_API_KEY` | empty | The events feature's own SerpApi key, separate from `SERP_API_KEY` used by trip planning. The app starts without it; requests then return 503. |
| `EVENTS_CACHE_TTL` | `PT24H` | How long a town's list is reused before fetching again. |
| `EVENTS_TIME_ZONE` | `Europe/Bucharest` | Zone for the provider's local event times. |
| `EVENTS_NOMINATIM_USER_AGENT` | `WalkAround-university-project/1.0 (upcoming events)` | Identifies the app to Nominatim, as its usage policy requires. |
| `EVENTS_REMINDERS_ENABLED` | `true` | Turns the reminder job on or off. |
| `EVENTS_REMINDER_TOPIC` | `event-reminders-topic` | Kafka topic shared with the realtime service. |

## Usage limits and attribution

- SerpApi's free plan allows 250 searches a month. A town costs two searches per cache period, and marking an event costs one. The `recorded` provider uses none.
- The public Nominatim service allows at most one request per second for the whole app, requires caching and an identifying User-Agent, and must not receive exact personal positions. `NominatimLocalityResolver` serializes calls, caches results and sends rounded coordinates. See the [Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/).
- Responses include an attribution line for Google Events via SerpApi and OpenStreetMap; the app shows it under the list.

## Tests

Unit tests in `src/test/java/services/applicationapi/events` cover date parsing, de-duplication and past-event filtering, cache order and stale fallback, reminder timing, and the recorded responses. They need no Docker and make no API calls.
