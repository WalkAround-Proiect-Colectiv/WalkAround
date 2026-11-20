package services.applicationapi.tripplan.service.savedplan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupPlanSavedEvent(
        UUID groupId,
        UUID planId,
        String groupName,
        String planTitle,
        Instant createdAt,
        List<UUID> recipientIds
) {
    public GroupPlanSavedEvent {
        recipientIds = List.copyOf(recipientIds);
    }
}
