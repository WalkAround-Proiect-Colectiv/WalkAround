# Pedestrian route provider

`RouteService` sends a pedestrian route request to the configured `app.valhalla.url` (`VALHALLA_URL`). The URL must include Valhalla's `/route` path. The service sends structured JSON with start, optional intermediate stops, and end latitude/longitude, pedestrian costing, kilometer units, and the configured `app.valhalla.language` (`VALHALLA_LANGUAGE`, default `en-US`). Letting Spring serialize the request avoids locale-dependent decimal formatting in the JSON body.

Valhalla returns the route JSON, including `trip.legs[].shape`, for the controller to forward. A non-success Valhalla response logs only its HTTP status here; the upstream body is not logged. The API proxy returns a generic client message for failures.
