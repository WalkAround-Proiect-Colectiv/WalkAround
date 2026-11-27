package services.applicationapi.tripplan.configurations;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.group-chat.documents")
public record GroupChatDocumentProperties(@DefaultValue("5242880") long maxBytes) {
    public GroupChatDocumentProperties {
        if (maxBytes < 1 || maxBytes > 5242880) throw new IllegalArgumentException("Document limit must be between 1 byte and 5 MiB");
    }
}
