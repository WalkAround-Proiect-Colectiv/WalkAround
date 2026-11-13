# Pedestrian route provider

`RouteServiceImpl` (implementing `RouteService`) sends a pedestrian route request to the configured `app.valhalla.url` (`VALHALLA_URL`). The URL must include Valhalla's `/route` path. The service sends structured JSON with start, optional intermediate stops, and end latitude/longitude, pedestrian costing, kilometer units, the configured `app.valhalla.language` (`VALHALLA_LANGUAGE`, default `en-US`), and optional `exclude_polygons` when avoiding active danger zones. Letting Spring serialize the request avoids locale-dependent decimal formatting in the JSON body.

Valhalla returns the route JSON, including `trip.legs[].shape`, for the controller to forward. A non-success Valhalla response logs only its HTTP status here (or maps `400 Bad Request` during danger avoidance to `NoSafeRouteException`); the upstream body is not logged on generic HTTP failures. The API proxy returns a generic client message for failures.
