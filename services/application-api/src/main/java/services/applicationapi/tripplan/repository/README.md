# Trip-plan repository

`TripPlanRepository` saves personal and group presets. `findByIdAndUser_IdAndGroupIsNull` scopes personal reads to their owner. Group queries scope by group ID; the service checks membership before calling them, so a group ID or plan ID alone does not grant access.

Keep owner scoping in repository lookups for future read, edit, or delete operations. The service maps a missing result to a client-friendly `404` without revealing whether the ID exists for another user.

The group-plan query lists presets newest first. Group creation locks the group before checking whether a current plan exists; the first plan is current. Admin selection clears the old current marker with a bulk update and then marks the chosen plan current in the same transaction. The database unique index enforces at most one current plan per group.

`TripPlanGroupRepository`, `TripPlanGroupMemberRepository`, and `TripPlanGroupPictureRepository` persist the group, its creator's `ADMIN` membership, and optional JPEG data. Group detail reads require a membership row; picture reads first check `existsByGroup_IdAndUser_Id`, so possession of a group ID does not grant access. The group service saves all three records in one transaction.

Membership queries list only groups joined by the authenticated user. `TripPlanGroupInviteRepository` looks up hashed tokens, counts active links, and lists recent invite metadata for admins. Invite creation, acceptance, and revocation acquire a group write lock to serialize changes; acceptance and revocation also lock the invite row. The initial acceptance lookup projects only the group ID, avoiding a stale invite entity in the persistence context before the locked read.

`TripPlanGroupMemberRepository.findUserIdsByGroupId` projects every current member ID when a new group plan is saved. The save service snapshots these IDs before commit so the notification publisher can route a separate message to each member without sending a group-wide broadcast.

Group editing also locks the group row and checks its admin membership before updating details. `TripPlanGroupPictureRepository` can save, replace, or delete the optional picture row in the same transaction.
