# POI Comments

Flat, plain-text comments live in the Application API database (Flyway V18), independently of the imported map database. Only public map POIs identified by a canonical UUID or a namespaced OSM type/ID can have a discussion. Names, coordinates, search display labels, and arbitrary client-generated IDs are not comment identities.

## Identity And Persistence

The existing read-only POI connection configured by `POI_DATABASE_URL`, `POI_DATABASE_USERNAME`, and `POI_DATABASE_PASSWORD` verifies references against `osm.features`, `places.published_pois`, and `places.published_osm_references`. Apply the map-data publication migrations before using this feature. An unavailable map connection returns 503; an unknown/unpublished place returns 404 rather than silently accepting a fabricated thread.

Application-owned subjects and aliases preserve discussions across tile refreshes and explicit OSM-to-canonical enrichment. When the published mapping establishes that existing aliases describe the same canonical place, their subjects/comments are combined in one main-database transaction. There is no proximity/name matching and no cross-database foreign key or write. A transaction-level advisory lock serializes alias reconciliation; this is deliberately simple for the initial feature and should be revisited if comment traffic becomes high.

## HTTP API

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/poi-comments?poiId=...&cursor=...` | Public, newest first, at most 20 comments |
| POST | `/api/poi-comments` | Signed in; body `{ "poiId": "osm:n/123", "body": "A useful tip" }` |
| PUT | `/api/poi-comments/{id}` | Author only; body `{ "body": "Updated tip" }` |
| DELETE | `/api/poi-comments/{id}` | Author or configured moderator |
| POST | `/api/poi-comments/{id}/reports` | Signed in; body `{ "reason": "Spam" }` |
| GET | `/api/poi-comments/reports?cursor=...` | Configured moderator only |

Page responses contain `items` and `nextCursor` (null at the end). Cursor ordering uses immutable creation time plus UUID, so equal timestamps and deletion of a previous page boundary do not duplicate or skip surviving items. Public comment responses expose only author UUID and username (or `WalkAround user`), never email, credentials, or profile details. HTTP responses are not cached.

Authors are derived from Bearer JWT subjects, never request bodies. Comments allow 1-2000 characters and report reasons 1-500, with server-side trimming and database constraints. A persistent, atomic per-user fixed-window limit allows ten creates, edits, or reports per minute across all POIs. Excess writes return 429. Deletion is not rate-limited. Each user can report a comment once; repeated reports do not create duplicate queue entries.

## Moderation

Set `POI_COMMENTS_MODERATOR_IDS` to a comma-separated list of existing user UUIDs in the API environment. It defaults to empty: ordinary users cannot access the report queue or delete others' comments. The report queue is an API, not an administrative frontend. A configured moderator authenticates normally, pages through `GET /api/poi-comments/reports`, and can remove a reported comment through the DELETE endpoint. Moderators cannot edit someone else's text. Deletion also removes the associated reports. There is no automatic takedown based on report counts.

## Tests

From this service, with Java 25: `./mvnw -Dtest='PoiComment*Test' test` (Windows: `mvnw.cmd`). Unit tests cover identity/body/cursor validation, ownership, moderation, rate-limit responses, and map failure handling. HTTP tests use the real security filter chain and validate public/protected routes and JWT-derived authors. PostgreSQL Testcontainers tests exercise V18, persistence, equal-timestamp pagination, alias merging, report deduplication, deletion cascades, and persistent rate limits; these tests require Docker and skip when it is unavailable.