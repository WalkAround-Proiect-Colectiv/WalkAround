# Login API transport

`http.ts` is the shared fetch helper. It adds the configured API base URL, converts non-2xx JSON problem responses to `ApiError`, and aborts calls after 15 seconds by default. Callers can pass a longer timeout as the fourth argument for a specific operation such as a chat image upload; normal calls retain the default.

`session.ts` holds the current access token in memory, refreshes it using the existing refresh flow, and retries an authenticated request once after a `401`. Its `authenticatedRequest` forwards the optional per-request timeout to `http.ts`, including on the retry. It does not persist access tokens in an image upload or expose them to ImageKit.

`transport.ts` and `transport.web.ts` own the platform-specific login and refresh requests. `types.ts` defines the session data shared with feature clients.
