package services.applicationapi.tripplan.service.exceptions;

public class GroupChatImageNotFoundException extends RuntimeException {

    public GroupChatImageNotFoundException() {
        super("Image message was not found");
    }
}
