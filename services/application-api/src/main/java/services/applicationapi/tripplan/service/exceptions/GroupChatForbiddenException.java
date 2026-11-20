package services.applicationapi.tripplan.service.exceptions;

public class GroupChatForbiddenException extends RuntimeException {
    public GroupChatForbiddenException() {
        super("Only the poll creator can close this poll");
    }
}
