package services.applicationapi.tripplan.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GroupChatPollResponse(
        long revision,
        Instant closedAt,
        UUID selectedOptionId,
        List<Option> options
) {
    public record Option(UUID id, String label, int votes) {
    }
}
