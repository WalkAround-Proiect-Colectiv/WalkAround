# Trip-plan repository

`TripPlanRepository` exposes only the operations required for saving and viewing a plan. `save` stores a new plan. `findByIdAndUser_Id` searches by both plan ID and authenticated owner ID, preventing a user from retrieving another user's plan even if they know its UUID.

Keep owner scoping in repository lookups for future read, edit, or delete operations. The service maps a missing result to a client-friendly `404` without revealing whether the ID exists for another user.

`TripPlanGroupRepository`, `TripPlanGroupMemberRepository`, and `TripPlanGroupPictureRepository` persist the group, its creator's `ADMIN` membership, and optional JPEG data. Group detail reads require a membership row; picture reads first check `existsByGroup_IdAndUser_Id`, so possession of a group ID does not grant access. The group service saves all three records in one transaction.

Membership queries list only groups joined by the authenticated user. `TripPlanGroupInviteRepository` looks up hashed tokens, counts active links, and lists recent invite metadata for admins. Invite creation, acceptance, and revocation acquire a group write lock to serialize changes; acceptance and revocation also lock the invite row. The initial acceptance lookup projects only the group ID, avoiding a stale invite entity in the persistence context before the locked read.

Group editing also locks the group row and checks its admin membership before updating details. `TripPlanGroupPictureRepository` can save, replace, or delete the optional picture row in the same transaction.
