# Map feature

`MapScreen` renders the map at `/map` using the platform-specific `MapCanvas` implementation. It loads the configured map style, adapts its resources in `style.ts`, and exposes preset locations, zoom, reset-north, and current-location controls. Map loading and location failures stay visible on the map so users can retry.

The features menu is a floating control inside the map viewport at the top left. Pressing it opens a vertical panel directly below the button, over the map without changing its layout. **Explore map** closes the panel, **Plan a trip** opens `/trip-plan`, and **My future** is a disabled placeholder. The brand and account link remain in the header above the map.
