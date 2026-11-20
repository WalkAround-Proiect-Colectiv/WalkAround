package services.applicationapi.tripplan.service.exceptions;

public class GroupChatInvalidMessageException extends RuntimeException {
    public GroupChatInvalidMessageException(String message) {
        super(message);
    }
}
