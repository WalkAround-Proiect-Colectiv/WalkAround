# POI Comments

Identified map POIs expose a Comments action in their detail panel. It opens a scrollable modal sheet on web and native, without navigating away from the map. Coordinate-only selections and current place-search suggestions do not have a durable POI identity and intentionally do not expose this action.

Anyone can read the newest comments, twenty at a time. Signed-in users can post plain text (1-2000 characters), edit and delete their own comments, and report another comment with a reason (1-500 characters). Deleting requires confirmation. Posting and reports use the existing authenticated request helper, including access-token refresh; reading is public. Bodies are rendered as text, not HTML. No ratings, replies, images, reactions, notifications, or realtime comment subscriptions are implemented.

`api.ts` keeps endpoint construction and public/authenticated transports separate. `PoiCommentsSheet.tsx` owns loading, empty, retry, pagination, editing, reporting, and submission state. The map keys the sheet by POI and signed-in user; request generations prevent results from a closed or changed discussion being applied later. Drafts are local to the open sheet and are discarded when it closes.

## Configuration

- Configure `EXPO_PUBLIC_API_BASE_URL` as for the other API features.
- Use the self-hosted map-data POI style, which publishes canonical UUIDs and `osm:n/<id>`, `osm:w/<id>`, or `osm:r/<id>` references. Hosted styles without this POI identity contract cannot expose comments.
- The Application API needs its main PostgreSQL database with migration V18 and read-only access to the matching map-data database through `POI_DATABASE_URL`, `POI_DATABASE_USERNAME`, and `POI_DATABASE_PASSWORD`. The existing recommendation POI connection is reused.
- Comments are not written into the map database or vector tiles. Approved published OSM-to-canonical aliases are resolved server-side so enrichment preserves discussions.

## Checks

From `frontend/`, run `npm run lint`, `npm run typecheck`, and `npm test`. Focused tests are `node --test tests/poi-comments.test.mjs tests/map.test.mjs`.

Browser/device checks should cover public reads, signed-out posting prompts, post/edit/delete, delete cancellation, report reasons, empty and failed loads, pagination, long text, the keyboard on mobile, and closing or switching POIs during a slow request. Native runtime verification requires a development build rather than Expo Go.