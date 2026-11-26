package services.applicationapi.tripplan.dto;

import java.util.UUID;
import services.applicationapi.tripplan.model.GroupChatMessage;

public record GroupChatReplyResponse(UUID messageId, String senderName, String messageType, String preview) {
    public static GroupChatReplyResponse from(GroupChatMessage message) {
        String text = switch (message.messageType()) {
            case "IMAGE" -> "Photo";
            case "POLL" -> "Poll: " + message.body();
            default -> message.body();
        };
        text = text.replaceAll("\\s+", " ").strip();
        String preview = text.codePoints().limit(160)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
        return new GroupChatReplyResponse(message.id(), message.senderName(), message.messageType(), preview);
    }
}