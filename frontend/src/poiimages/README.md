# POI photos (SCRUM-42)

A **Photos** section in the POI details card on the map. The backend is described in the [POI photos backend README](../../../services/application-api/src/main/java/services/applicationapi/poiimages/README.md).

## When it appears

The section is added to the card only for real POIs from the self-hosted map: a curated POI's UUID or an OpenStreetMap id such as `osm:n/123` (`canHavePhotos` in `limits.ts`, the same rule the backend uses). It does not appear for search results, for a tapped map coordinate ("Selected location"), or on the default hosted OpenFreeMap map, which has no POI ids.

Viewing and adding photos both require sign-in. Signed-out users see a link to sign in instead.

## Behaviour

- Photos load when the card opens for a POI and show as a scrollable row of thumbnails, newest first, with "No photos yet" when there are none. Errors show a **Try again** link.
- **Add photo** opens the device's photo library (`expo-image-picker`; on web, a file picker). The chosen photo shows as a preview with an optional caption (up to 200 characters) and **Upload** / **Cancel**. Files over 5 MB are refused before uploading.
- Tapping a thumbnail opens the photo full size (if ImageKit cannot deliver it, an error is shown) with its caption, who added it and when. Your own photos have **Delete photo**, with a confirmation step.
- The section is keyed by the POI id, so switching POIs (including the ‹ › pager for nearby POIs) resets it.

The photo files are stored in ImageKit. The list response contains signed ImageKit URLs for each photo and its thumbnail, which the app loads directly; they expire after about an hour, and the list is fetched again whenever the card opens. Only `https://` URLs are shown (`isSafeImageUrl`).

## Files

- `components/PoiPhotos.tsx`: the section, thumbnails and full-size viewer.
- `api/poiImages.ts`: list, upload and delete.
- `limits.ts`: upload and caption limits, the POI id check and the https check, shared with the tests.

## Tests

`tests/poiImages.test.mjs` checks that the upload and caption limits match the backend configuration and database column, and which ids may have photos. Run `npm test`.
