package services.applicationapi.tripplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import java.util.List;

public record GroupChatMessageRequest(
        @NotNull UUID messageId,
        @NotBlank @Size(max = 2000) String body,
        String messageType,
        List<String> options
) {
}
