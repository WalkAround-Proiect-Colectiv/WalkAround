package services.applicationapi.tripplan.service.exceptions;

public class GroupChatPollNotFoundException extends RuntimeException {
    public GroupChatPollNotFoundException() {
        super("Poll was not found");
    }
}
