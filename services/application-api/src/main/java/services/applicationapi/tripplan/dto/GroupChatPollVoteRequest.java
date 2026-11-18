package services.applicationapi.tripplan.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GroupChatPollVoteRequest(@NotNull UUID optionId) {
}
