package services.applicationapi.tripplan.service.exceptions;

public class GroupChatDocumentNotFoundException extends RuntimeException {
    public GroupChatDocumentNotFoundException() { super("Document not found in this group"); }
}
