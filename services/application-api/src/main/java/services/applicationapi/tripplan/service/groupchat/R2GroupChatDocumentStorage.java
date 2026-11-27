package services.applicationapi.tripplan.service.groupchat;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import services.applicationapi.tripplan.configurations.GroupChatR2Properties;
import services.applicationapi.tripplan.service.exceptions.GroupChatDocumentStorageException;

@Component
@EnableConfigurationProperties(GroupChatR2Properties.class)
public class R2GroupChatDocumentStorage implements GroupChatDocumentStorage {
    private static final Logger log = LoggerFactory.getLogger(R2GroupChatDocumentStorage.class);
    private final GroupChatR2Properties properties;
    private final S3Client client;
    private final S3Presigner signer;

    public R2GroupChatDocumentStorage(GroupChatR2Properties properties) {
        this.properties = properties;
        if (!properties.configured()) { client = null; signer = null; return; }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
        var config = S3Configuration.builder().pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build();
        client = S3Client.builder().endpointOverride(URI.create(properties.endpoint())).region(Region.of("auto"))
                .credentialsProvider(credentials).serviceConfiguration(config)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(c -> c.apiCallTimeout(properties.timeout()).apiCallAttemptTimeout(properties.timeout()))
                .build();
        signer = S3Presigner.builder().endpointOverride(URI.create(properties.endpoint())).region(Region.of("auto"))
                .credentialsProvider(credentials).serviceConfiguration(config).build();
    }

    public StoredDocument upload(UUID groupId, UUID messageId, byte[] data, String extension, String contentType) {
        requireConfigured();
        String key = "group-chat-documents/" + groupId + "/" + messageId + "/" + UUID.randomUUID() + "." + extension;
        var stored = new StoredDocument(key, properties.bucket());
        try {
            client.putObject(r -> r.bucket(stored.bucket()).key(key).contentType(contentType)
                    .contentDisposition("attachment").cacheControl("private, no-store"), RequestBody.fromBytes(data));
            return stored;
        } catch (SdkException exception) {
            // A timed-out request may have reached R2. Best-effort removal uses this upload's unique key.
            delete(stored);
            log.warn("R2 document upload failed for message {}: {}", messageId, exception.getClass().getSimpleName());
            throw new GroupChatDocumentStorageException("Document storage is temporarily unavailable");
        }
    }

    public String signedUrl(String bucket, String objectKey, String fileName) {
        requireConfigured();
        if (!properties.bucket().equals(bucket) || !objectKey.startsWith("group-chat-documents/")) {
            throw new GroupChatDocumentStorageException("Document storage configuration does not match this attachment");
        }
        var request = GetObjectRequest.builder().bucket(bucket).key(objectKey)
            .responseContentDisposition(ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
            .responseCacheControl("private, no-store").build();
        return signer.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(properties.signedUrlTtl())
            .getObjectRequest(request).build()).url().toString();
    }

    public void delete(StoredDocument document) {
        if (client == null || !properties.bucket().equals(document.bucket()) || !document.objectKey().startsWith("group-chat-documents/")) return;
        try { client.deleteObject(r -> r.bucket(document.bucket()).key(document.objectKey())); }
        catch (SdkException exception) { log.warn("Could not remove unused R2 document {}: {}", document.objectKey(), exception.getClass().getSimpleName()); }
    }

    private void requireConfigured() {
        if (client == null) throw new GroupChatDocumentStorageException("Document uploads are not configured");
    }
    @PreDestroy public void close() { if (client != null) client.close(); if (signer != null) signer.close(); }
}
