package services.applicationapi.tripplan.service.tripgroup;

import java.util.List;
import java.util.UUID;
import services.applicationapi.tripplan.dto.TripGroupInvitePreview;
import services.applicationapi.tripplan.dto.TripGroupInviteResponse;
import services.applicationapi.tripplan.dto.TripGroupInviteSummary;
import services.applicationapi.tripplan.dto.TripGroupResponse;

public interface TripGroupInviteService {

    TripGroupInviteResponse create(UUID groupId, UUID adminId);

    List<TripGroupInviteSummary> list(UUID groupId, UUID adminId);

    void revoke(UUID groupId, UUID inviteId, UUID adminId);

    TripGroupInvitePreview preview(String token);

    TripGroupResponse accept(String token, UUID userId);
}
