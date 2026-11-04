# Trip-plan repository

`TripPlanRepository` exposes only the operations required for saving and viewing a plan. `save` stores a new plan. `findByIdAndUser_Id` searches by both plan ID and authenticated owner ID, preventing a user from retrieving another user's plan even if they know its UUID.

Keep owner scoping in repository lookups for future read, edit, or delete operations. The service maps a missing result to a client-friendly `404` without revealing whether the ID exists for another user.
