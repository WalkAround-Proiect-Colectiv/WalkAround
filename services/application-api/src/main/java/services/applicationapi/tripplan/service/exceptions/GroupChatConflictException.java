package services.applicationapi.tripplan.service.exceptions;

public class GroupChatConflictException extends RuntimeException {
    public GroupChatConflictException() {
        super("This message ID was already used for different content");
    }
}
