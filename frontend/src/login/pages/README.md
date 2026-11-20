# Login page

`LoginScreen` signs a user in with the existing Google session flow. Once the session is ready, it checks `tripplan/api/pendingGroupInvite.ts` for an invitation opened before sign-in. A pending invite routes to `/trip-group-invite`; otherwise sign-in routes to `/map`. The invite screen owns preview, acceptance, and clearing the pending token. Registration returns to this login page, so an invite opened by a new user is preserved through account creation and sign-in.
