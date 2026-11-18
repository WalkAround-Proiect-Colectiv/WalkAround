# Trip group creation

`TripGroupService` creates a trip group for the authenticated user. `TripGroupServiceImpl` validates and trims its name and optional description, normalizes an optional JPEG or PNG picture, and saves the group, an `ADMIN` membership for its creator, and the picture in one database transaction. A failed validation or write leaves no partial group. A member can read one group by ID, which makes the create response's `Location` usable. Groups currently have no invitations, list view, chat, or shared planner.

Names are required, at most 100 characters, and cannot contain control characters. Descriptions are optional and at most 1,000 characters; line breaks and tabs are allowed. `TripGroupProperties` bounds upload bytes, decoded pixels, and output dimensions. Images are detected from their contents, re-encoded as JPEG without metadata, and stored in `trip_plan_group_pictures` rather than loaded with every group row.

Group detail and picture reads check membership first. A user who is not a member gets the same `404` response as an unknown group; an absent picture also returns `404`. The application JWT supplies the user ID; request data never selects an owner or initial role. Responses use `Cache-Control: no-store`. No invite or chat privileges are exposed yet.
