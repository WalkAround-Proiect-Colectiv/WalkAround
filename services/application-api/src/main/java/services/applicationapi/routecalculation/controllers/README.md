# Route proxy

`RouteProxyController` forwards `POST /api/v1/routes/pedestrian` to the routing service configured by `ROUTING_SERVICE_URL`. A successful response preserves the route JSON. If the routing service responds with an error or cannot be reached, the proxy logs the HTTP status or failure category and returns a generic `502` Problem Detail. Unexpected proxy failures return a generic `500`. Upstream error bodies are not exposed to clients.
