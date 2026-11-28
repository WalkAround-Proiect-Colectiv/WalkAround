package services.applicationapi.tripplan.configurations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.group-chat.documents")
public record GroupChatDocumentProperties(@DefaultValue("5242880") long maxBytes,
        @DefaultValue("PT5M") Duration signedUrlTtl) {
    public GroupChatDocumentProperties {
        if (signedUrlTtl.isNegative() || signedUrlTtl.isZero() || signedUrlTtl.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Document URL lifetime must be positive and at most one hour");
        if (maxBytes < 1 || maxBytes > 5242880) throw new IllegalArgumentException("Document limit must be between 1 byte and 5 MiB");
    }
}
