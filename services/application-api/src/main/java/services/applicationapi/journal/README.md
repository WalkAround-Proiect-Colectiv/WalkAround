# Private Journal Trips

Journal trips belong to one user and are stored independently from saved planning presets. Flyway V21 adds `journal_trips` with an owner foreign key, required title/description/location, date-only duration, an optional personal source-plan reference, timestamps, and a creation-request fingerprint. Deleting a source plan clears only the reference; deleting a journal trip does not delete or change its source.

## Rules And Reuse

- Name and description are required; limits are 120 and 2000 characters, with the planner's text/control-character policy.
- Country codes must exist in the same local catalog used by the planner. Its current importer loads EU members only.
- Manual and changed destinations reuse `CityNameNormalizer` and `DestinationCityValidator`. An unchanged destination from an authorized saved plan or journal trip is already validated and does not make another provider request.
- Creation and updates require a start on or after the journal's UTC reference day. End must be on or after start; same-day trips are valid. Dates are `LocalDate`, not elapsed hours or UTC timestamps.
- Import accepts only a source returned by the existing owner-authorized personal-plan lookup. Shared/group plans cannot be imported.
- The user explicitly chooses `startMode: PLANNED | CUSTOM` and `keepNights: true | false`. PLANNED enforces the saved departure dates; CUSTOM allows any non-past start, including for an expired plan.
- Keeping nights computes start plus saved nights server-side. Otherwise `endDate` is mandatory. Name, description, and destination may differ from the source; blank source descriptions do not waive the journal's required description.
- Imports copy values and have no live synchronization with the source. Editing uses normal dates and cannot replace the original source-plan reference.

## API

Every endpoint requires a Bearer access JWT. Reads use `Cache-Control: no-store`; the owner is never accepted from a request body. Unknown and other users' trip IDs return 404.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/journal/trips/settings` | `{today,timeZone}` for consistent date controls; UTC |
| GET | `/api/journal/trips?page=0` | Owner-only `{items,nextPage}`, 20 items per page |
| GET | `/api/journal/trips/{id}` | Private detail |
| POST | `/api/journal/trips` | Manual creation or personal-plan import |
| PUT | `/api/journal/trips/{id}` | Owner-only full-field edit |
| DELETE | `/api/journal/trips/{id}` | Owner-only deletion |
| GET | `/api/trip-plans?page=0` | Owner-only personal plan picker; group plans excluded |

POST body:

```json
{
  "requestId": "11111111-1111-4111-8111-111111111111",
  "title": "Weekend in Cluj",
  "description": "Explore the city with friends.",
  "destinationCity": "Cluj-Napoca",
  "countryCode": "RO",
  "startDate": "2030-06-01",
  "endDate": "2030-06-01",
  "sourcePlanId": null,
  "startMode": null,
  "keepNights": null
}
```

For import, set `sourcePlanId`, `startMode`, and `keepNights`. If keeping nights, `endDate` may be null because the source determines it. PUT uses the route trip ID as `requestId`, with import-only fields null; it retains existing provenance independently.

The request UUID is also the new trip's ID. A transaction-level user-row lock serializes creates, and a SHA-256 fingerprint of the structured request distinguishes an identical retry from conflicting reuse (409). Repeat imports with new UUIDs are allowed. A network retry of an already saved request returns the current resource without re-importing its source. No creation retry updates an existing trip.

## Verification

Use Java 25 and the module wrapper: `./mvnw '-Dtest=Journal*Test' test` (Windows: `mvnw.cmd`). Unit tests cover date boundaries/leap days, private ownership, mandatory fields, catalog validation, import confirmation and duplicate submissions. HTTP security tests use the production security chain. `JournalFlowIntegrationTest` boots the API, applies V21 to disposable PostgreSQL, signs test JWTs, and exercises CRUD, personal-only plan listing/import, expired windows, computed dates, repeat imports, source deletion, and unauthorized access. The existing external destination and image-storage boundaries are mocked, not the database.

Database tests require Docker and default to `postgres:18`. A cached compatible image can be selected with `-Djournal.test-image=postgis/postgis:17-3.5`. Skips caused by missing Docker must not be reported as passed database verification. No extra production service or scheduler is added.