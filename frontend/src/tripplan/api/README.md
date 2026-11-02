# Trip planning API client

`tripPlanning.ts` defines the JSON shapes used by the page and calls the three existing trip-planning endpoints: city autocomplete, city airports, and flight search. It URL-encodes city names and country codes, sends the current application JWT from the shared session module, applies a 90-second request timeout, and turns backend `ProblemDetail.detail` into a page error. It does not call GeoNames or SerpApi directly, store a token, or persist a search preset.
