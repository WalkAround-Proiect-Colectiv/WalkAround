# POI Comments

Flat, plain-text comments live in the Application API database (Flyway V19), independently of the imported map database. Only public map POIs identified by a canonical UUID or a namespaced OSM type/ID can have a discussion. Names, coordinates, search display labels, and arbitrary client-generated IDs are not comment identities.

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

From this service, with Java 25: `./mvnw -Dtest='PoiComment*Test' test` (Windows: `mvnw.cmd`). Unit tests cover identity/body/cursor validation, ownership, moderation, rate-limit responses, and map failure handling. HTTP tests use the real security filter chain and validate public/protected routes and JWT-derived authors. PostgreSQL Testcontainers tests exercise V19, persistence, equal-timestamp pagination, alias merging, report deduplication, deletion cascades, and persistent rate limits; these tests require Docker and skip when it is unavailable.

`PoiCommentFlowIntegrationTest` boots the complete Application API, applies photos V18 and comments V19 together, signs test JWTs, and exercises public reading, authenticated posting, ownership, editing, deletion, reporting, and moderation over Spring MVC against real PostgreSQL. Only the unrelated external photo storage is mocked; no Google, ImageKit, or Kafka service is required by these tests.

The database tests default to `postgres:18`. If Docker Hub is unavailable and a compatible PostgreSQL image is already cached, override `poi.comments.test-image`, for example in PowerShell:

```powershell
.\mvnw.cmd '-Dtest=PoiComment*Test' '-Dpoi.comments.test-image=postgis/postgis:17-3.5' clean test
```

Run `clean` after updating from the earlier branch migration name so a stale generated V18 comments resource is removed from `target/`. Comments now use V19 because main's photos feature owns V18. If a database already applied the branch-only V18 comments migration, back it up and coordinate its migration-history upgrade before using the merged branch; do not blindly repair Flyway history or delete tables. Databases which applied main's V18 photos migration can apply V19 normally.