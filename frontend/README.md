# Frontend

The frontend is an Expo SDK 58 / React Native application that targets iOS, Android, and web. Expo Router uses file-based routes in `src/app/`; those route files delegate to screens and feature code outside the route directory.

## Main Paths

- `src/app/`: route entry points. `/` redirects to the public `/map` prototype; `/login`, `/register`, and `/home` retain the login, registration, and protected account screens.
- `src/map/`: shared map screen, camera configuration, flat style customization, and separate MapLibre web/native renderer adapters.
- `src/auth/`: Google sign-in button implementations, including a web-specific version.
- `src/login/`: session state, API transports, and account/login screens.
- `src/register/`: Google-backed registration API and registration screen.
- `src/config/environment.ts`: reads the public API URL and Google web/iOS client IDs.

The root layout wraps routes in `SessionProvider`. It attempts to restore a session at startup and refreshes a near-expired session when the app returns to the foreground. Protected API calls use the access token; the refresh credential is stored differently per platform:

- Native: SecureStore holds the refresh token.
- Web: the API sets an HttpOnly refresh cookie; requests include credentials.

## Configuration

Set these Expo public environment variables before starting the app:

- `EXPO_PUBLIC_API_BASE_URL`: base URL of the Application API, without a trailing slash.
- `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`: Google web OAuth client ID.
- `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID`: Google iOS OAuth client ID; also used to derive the iOS URL scheme.
- `EXPO_PUBLIC_MAP_STYLE_URL`: optional absolute style JSON URL. Defaults to OpenFreeMap's hosted Liberty style for the prototype; no key is required. Backend and Google credentials are not required to browse `/map`.

Android application ID and iOS bundle identifier can be set with `ANDROID_APPLICATION_ID` and `IOS_BUNDLE_IDENTIFIER`. Native Google sign-in also requires the matching package/bundle identifiers and signing configuration to be registered with Google.

## Commands

Run these from `frontend/`:

```sh
npm install
npm run start
npm run web
npm run android
npm run ios
npm run lint
npm run typecheck
npm test
```

The Android and iOS commands launch Expo for the selected platform. Google Sign-In uses a native module, so native testing may require an Expo development build rather than Expo Go. See the local `AGENTS.md` for the workspace's Expo-specific implementation rules.

## Map Prototype

From this directory, run `npm install` and `npm run web`, then open `/map` at the URL printed by Expo. On Windows PowerShell, use `npm.cmd` / `npx.cmd` if the script execution policy blocks the `.ps1` launchers. The root route opens the map without signing in; the account icon leads to the existing authentication flow.

The prototype uses MapLibre GL JS on web and MapLibre Native on Android/iOS. Both consume a shared, customized OpenFreeMap vector style with blue water, white roads, gray buildings/outlines, green land cover, and city/road/POI labels. Shaded relief, elevation sources, and 3D extrusions are removed. Zoom-dependent feature visibility comes from the style and tile data, so buildings are not expected at country scale.

The camera initially fits a rectangular Romania coverage area. Country/city presets, pan, pinch/scroll zoom, zoom buttons, north reset, and foreground-only location centering are available. Location is requested only when the user presses the location button; denial or positions outside the coverage rectangle show an error without preventing manual browsing. This rectangle includes some neighboring territory and is not an exact country boundary. Camera movement and resizing let MapLibre choose visible tiles and nearby cache/prefetch tiles; no whole-country dataset is downloaded. The renderer may retain previously viewed tiles in its normal cache; this is not offline-pack support.

The hosted source has worldwide coverage and manual panning is not geographically restricted; Romania is the initial focus, not a tile-download boundary. Zoom is limited to 4-18. The footer reports camera latitude, longitude, and zoom. Map loading, style timeout, resource errors, and retry states are included. Attribution is supplied by the tile source and remains available through the map attribution control.

### Native Builds

MapLibre Native cannot run in Expo Go. Build a development client after installing dependencies and applying the configured MapLibre/location plugins:

```sh
npx expo run:android
# On macOS with Xcode:
npx expo run:ios
```

Android requires its SDK/emulator or a connected device; iOS local builds require macOS. Web can be tested independently on Windows. The native adapters are typechecked, but native device rendering and permission prompts still require device/emulator testing.

### Worker Assets and Self-Hosting

MapLibre GL JS 6 requires a module worker. The install/start scripts copy its version-matched worker and shared bundle into the ignored `public/maplibre/` directory, served from the same origin. Run `node scripts/prepare-map-assets.mjs` before a direct `npx expo export --platform web` or after changing the renderer version. Deploy the exported worker assets with the app; root-relative `/maplibre/` URLs currently assume root-path hosting.

The self-hosted alternative is implemented in [the map-data module](../services/map-data/README.md): a dedicated PostGIS database, full regional osm2pgsql import, regional replication-diff updates, a Flyway-managed tile function, Martin, local glyphs, and a matching style. It needs a running Docker engine and a verified import before use; hosted OpenFreeMap remains the default during rollout.

After following the map-data setup, set `EXPO_PUBLIC_MAP_STYLE_URL=http://localhost:3001/style.json` in the frontend's local environment and restart Expo. Use an accessible LAN/emulator host instead of localhost on other devices. The frontend resolves source URLs, tile templates, glyphs, and sprites against the style response URL, preserving MapLibre placeholders. The self-hosted `features` vector layer is not OpenMapTiles-compatible; use its supplied style, not Liberty or a raw Martin tile URL. Glyphs are served locally and the supplied style needs no sprites. Expect less complete styling than the hosted prototype, particularly around coastlines and detailed transport ordering.

Offline map downloads, elevation/3D terrain, Valhalla routing, H3 reports, and background location remain out of scope. Offline and elevation support are deferred to future stories; tile import/update operations now belong to the separate map-data module.

### Verification

Run `npm run lint`, `npm run typecheck`, and `npm test`. Map tests cover preset coordinates, zoom ranges, coverage validation, and non-mutating removal of relief/extrusions. Browser checks should cover Romania/city views, z14 to z15 zoom, dragging and resize, location grant/denial/out-of-area handling, failed loading/retry, and visible attribution. Inspect network traffic for viewport-related `z/x/y.pbf` requests rather than a full regional download. Native runtime coverage remains separate from web browser verification.

## TO BE ADDED:

Use the following design direction as the app's shared template. Apply it as consistently as each feature's purpose and platform constraints allow. Feature designs may introduce additional colors when they work with the overall palette and theme.

### Color Palette

- **Primary:** Bright blue (`#0065EB`) for brand elements, action buttons, and interactive text.
- **Secondary:** Bright red (`#FF0000`) for danger zones, the report button, and the logout button; vibrant purple (`#23012C`) for the route displayed on the map.
- **Neutrals:** Clean white and light grays for app backgrounds, map base, and text inputs; dark grays for outlines and supporting details.

### Map

Use Waze's color palette as a reference, favoring simple, elegant colors. Initial map coverage should focus on Romania to keep map data, storage, and memory requirements manageable. Aim to make country coverage data-driven, so neighboring countries can be added mostly by supplying additional map data rather than introducing country-specific application logic. Use OpenStreetMap for underlying map features such as POIs, streets, and roads; the separate map-data pipeline performs an initial PostGIS import with `osm2pgsql` and supports weekly regional replication-diff updates.

Use MapLibre GL to render tiles supplied by the planned Martin tile server. Martin serves map data; MapLibre GL renders it. Load tiles for the visible map area rather than loading the full country dataset into app memory. Represent dangerous-area reports with H3 cells instead of ad hoc circles, choosing a resolution that communicates their coverage clearly.

Valhalla is the planned routing engine. Routing should account for report severity: lower-risk areas may warrant a warning, while higher-risk areas can influence route choice. Keep this behavior behind a clear routing API and verify the required Valhalla costing/integration approach when implementing it. GraphHopper is an alternative previously proposed for user-weighted hazard routing; do not switch routing engines without revisiting the decision with the team.

### Icons

- Prefer icons with rounded corners.
- Use a simple, cartoonish style for icons representing dangerous-area report types.

### Shared Design Reference

Use the [WalkAround Figma file](https://www.figma.com/design/2ioH2AXPaJgpHuhT7ArsQZ/Walkaround?node-id=13-151&t=oYchqovdhky94ITE-1) as the starting point for the app's visual design, including its comments. Design each feature to follow the shared theme, palette, and icon style where practical.

### Logo

The logo concept is a relaxed traveler exploring the world with WalkAround.
