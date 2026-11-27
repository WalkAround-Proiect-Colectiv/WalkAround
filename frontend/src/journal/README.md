# Private Journal Trips

Open **Private journal** from the map's features menu. `/journal` lists only the signed-in user's trips. `/journal/new` supports manual creation and importing a personal saved plan; `/journal/[id]` shows a trip with Edit and confirmed Delete actions. Signed-out users are redirected to login. Expenses, bookings, photos, and attraction collections are not implemented by this feature.

## Creation And Import

Name, description, a selected destination, and start/end dates are mandatory. Names allow 120 characters and descriptions 2000, matching the planner's field sizes. Destination search uses the planner's `searchCities` client and its existing GeoNames/country-catalog validation. There is no separate geographic provider or additional API key. The allowed countries are the planner's currently imported EU-only catalog, not all geographic Europe.

Manual creation requires choosing both dates; the end can equal the start. Past starts are rejected. The journal settings endpoint supplies the current UTC calendar date and the shared CalendarPicker accepts that reference day, so device time zones cannot shift the journal's minimum date. CalendarPicker's existing planner defaults are unchanged.

Import lists all personal saved plans with pagination, including expired windows and empty descriptions. Group plans are excluded. Selecting a plan prefills its name, description, and destination, all of which remain editable. Missing descriptions must be entered. The user explicitly chooses:

- **Saved departure dates**: start must fall in the departure window on or after today. Expired windows disable this option.
- **Custom date**: any start of today or later, even when the source window expired.
- **Yes, keep N nights**: the UI previews start plus N calendar days; the API computes the authoritative end date.
- **No, choose an end date**: an explicit end is required; same-day trips are allowed.

Imports are independent snapshots. Editing a journal trip does not modify the source plan and uses normal journal date fields rather than repeating import choices. A plan may be imported repeatedly for different trips. Deleting a source plan does not delete journal trips. Existing journal records remain readable after their dates pass; creation and edits enforce the no-past-start rule.

## Reliability And Privacy

The existing authenticated request helper handles all API calls. The backend derives the owner from the access JWT. Client fields contain no owner or group ID. Loading, empty, retry, and error states are provided, and user changes/unmounting prevent old requests from navigating into a different user's journal.

A create attempt gets a client UUID. Retrying an unchanged failed submission reuses that ID; editing the payload creates a new attempt. The API serializes creation per owner and compares a stored request fingerprint to prevent duplicate creation on retries. Intentional repeat imports use fresh IDs. Form values remain present after failed saves. Deletion requires explicit confirmation.

## Checks

From `frontend/`: `npm run lint`, `npm run typecheck`, `npm test`. Focused date/API tests: `node --test tests/journal.test.mjs`. Browser checks cover manual and imported creation, mandatory descriptions, past/window date bounds, saved-night confirmation, editable fields, retry IDs, owner edit/delete UI, and desktop/mobile overflow. Database and ownership integration tests live under the Application API's `journal` tests and require Docker.