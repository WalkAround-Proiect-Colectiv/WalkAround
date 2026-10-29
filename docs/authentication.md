# Authentication contracts

Registration and login are separate operations. Google registration creates the
internal user and external identity. Google login requires that identity to exist
and never overwrites the user's profile or creates another account.

## Endpoints

| Method and path | Request | Response |
| --- | --- | --- |
| POST `/api/registrations/google` | `{ "idToken": "Google ID token" }` | 201 profile; 409 if registered |
| POST `/api/sessions/google` | `{ "idToken": "Google ID token" }` | Web session + HttpOnly refresh cookie |
| POST `/api/sessions/refresh` | Refresh cookie | Rotated web session + replacement cookie |
| POST `/api/sessions/logout` | Refresh cookie | 204; session revoked and cookie cleared |
| POST `/api/mobile/sessions/google` | `{ "idToken": "Google ID token" }` | `{ "session": ..., "refreshToken": ... }` |
| POST `/api/mobile/sessions/refresh` | `{ "refreshToken": "..." }` | Rotated mobile session and refresh token |
| POST `/api/mobile/sessions/logout` | `{ "refreshToken": "..." }` | 204; session revoked |
| GET `/api/users/me` | `Authorization: Bearer <accessToken>` | Current user's profile |

A session contains `accessToken`, `tokenType` (`Bearer`), `expiresAt` (UTC ISO
timestamp), and `user`. Token responses use `Cache-Control: no-store`.
Login returns 404 for an unregistered Google identity, 401 for invalid identity
credentials, and 503 if the signing key or Google client IDs are not configured.
Invalid, expired, revoked, already-used, or wrong-platform refresh tokens return 401.

## Token lifecycle

- Access tokens are application-issued HS256 JWTs. They contain the internal user
  UUID, issuer, audience, timestamps, token ID, and `token_use=access`.
- The signing secret is at least 32 cryptographically random bytes, encoded as
  Base64. It belongs only on trusted servers. Google ID tokens are never accepted
  as application Bearer tokens.
- Access tokens default to 15 minutes; refresh sessions default to an absolute
  30-day lifetime. Refreshing does not extend that lifetime.
- Flyway V2 adds `refresh_sessions`. Each sign-in creates a distinct session.
  Only a SHA-256 hash of the refresh token is stored. The raw token has a random
  256-bit secret and is returned only to its client.
- Refresh locks the session row and replaces the stored hash atomically. Only
  one concurrent refresh with the same token succeeds. Old tokens are rejected;
  an old-token attempt does not revoke the current valid replacement.
- Logout revokes the current refresh session. Already-issued access JWTs remain
  valid until expiry; logout does not introduce a database lookup on every request.
  Other devices' sessions are unaffected.
- Expired/revoked rows remain available for audit; a retention cleanup job is not
  part of this ticket.

## Browser and mobile handling

Web refresh tokens use a host-only HttpOnly, SameSite=Strict cookie scoped to
`/api/sessions`. Web endpoints independently require an allowed Origin on every
POST, including refresh and logout. CORS permits configured origins and credentials.
The browser uses `credentials: include` and stores access tokens only in memory.
The browser frontend and API must be deployed on the same site for this cookie
policy. `AUTH_COOKIE_SECURE` must be true for deployed HTTPS environments.

Mobile endpoints accept refresh credentials only in JSON, never ambient cookies.
Their refresh tokens are bound to the MOBILE client type and stored in Expo
SecureStore. Web refresh tokens cannot be exchanged through mobile endpoints.

The frontend restores sessions at startup, serializes refresh requests, and
refreshes near-expired access tokens before authenticated API calls. It retries
an authenticated request once on 401. Browser Web Locks serialize refresh across
tabs where supported. The native build must include SecureStore and Google sign-in;
browser testing cannot validate device keychain behavior.

## Configuration and frontend routes

See `services/application-api/.env.example` and `frontend/.env.example` for all
identity, token-lifetime, cookie, origin, and public-client variables. Real values
belong in ignored local environment files or deployment secrets, not the examples.
No Google client secret is needed for this ID-token flow.

Expo Router provides `/login`, `/register`, and `/home`. Unauthenticated home
navigation returns to login. Successful registration links to login; it does not
implicitly create a session. Feature clients can reuse `authenticatedRequest`
from `frontend/src/login/api/session.ts`.

## Test-owner handoff

The pre-existing registration test hard-codes one successful Flyway migration
and a 403 for unauthenticated access. With V2 and the Bearer-token filter, those
expectations are two migrations and 401. The test owner should update these
assertions (or make the migration assertion independent of total migration count).

Temporary login checks covered JWT claims/signatures, unknown users, profile
preservation, rotation, concurrent refresh, expiry, logout, cookie flags, origin
checks, and separation between web and mobile refresh credentials. Temporary
browser checks covered the login/refresh/reload/logout flow and routing at a mobile
viewport with mocked Google/API responses. These temporary tests were deleted
after execution; no new tests are included in the feature commits.
