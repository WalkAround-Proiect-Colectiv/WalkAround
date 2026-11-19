package services.applicationapi.tripplan.service.exceptions;

public class GroupChatImageStorageException extends RuntimeException {

    public GroupChatImageStorageException(String message) {
        super(message);
    }

    public GroupChatImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
