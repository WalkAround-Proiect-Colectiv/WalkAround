package services.applicationapi.tripplan.configurations;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.group-chat.r2")
public record GroupChatR2Properties(@DefaultValue("") String endpoint, @DefaultValue("") String bucket,
        @DefaultValue("") String accessKeyId, @DefaultValue("") String secretAccessKey,
        @DefaultValue("PT5M") Duration signedUrlTtl, @DefaultValue("PT20S") Duration timeout) {
    public GroupChatR2Properties {
        if (signedUrlTtl.isNegative() || signedUrlTtl.isZero() || signedUrlTtl.compareTo(Duration.ofHours(1)) > 0
                || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Invalid R2 timeout or signed URL lifetime");
        }
        if (!endpoint.isBlank()) {
            URI uri = URI.create(endpoint);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
                throw new IllegalArgumentException("R2 endpoint must be an HTTPS S3 endpoint without a bucket path");
            }
        }
    }
    public boolean configured() {
        return !endpoint.isBlank() && !bucket.isBlank() && !accessKeyId.isBlank() && !secretAccessKey.isBlank();
    }
    @Override public String toString() { return "GroupChatR2Properties[credentials redacted]"; }
}
