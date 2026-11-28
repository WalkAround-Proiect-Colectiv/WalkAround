package services.applicationapi.tripplan.service.groupchat;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import services.applicationapi.tripplan.configurations.ImageKitProperties;
import services.applicationapi.tripplan.configurations.GroupChatDocumentProperties;
import services.applicationapi.tripplan.service.exceptions.GroupChatDocumentStorageException;
import tools.jackson.databind.JsonNode;

@Component
public class ImageKitGroupChatDocumentStorage implements GroupChatDocumentStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageKitGroupChatDocumentStorage.class);

    private final RestClient client;
    private final ImageKitProperties properties;
    private final GroupChatDocumentProperties documents;

    public ImageKitGroupChatDocumentStorage(@Qualifier("imageKitRestClient") RestClient client,
                                        ImageKitProperties properties, GroupChatDocumentProperties documents) {
        this.client = client;
        this.properties = properties;
        this.documents = documents;
    }

    @Override
    public StoredDocument upload(UUID groupId, UUID messageId, byte[] data, String extension, String contentType) {
        requireConfigured();
        String folder = "/walkaround/group-chat-documents/" + groupId;
        String fileName = messageId + "-" + UUID.randomUUID() + "." + extension;
        String expectedPath = folder + "/" + fileName;

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        ByteArrayResource file = new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        var parts = new LinkedMultiValueMap<String, Object>();
        parts.add("file", new HttpEntity<>(file, fileHeaders));
        parts.add("fileName", fileName);
        parts.add("folder", folder);
        parts.add("useUniqueFileName", "false");
        parts.add("overwriteFile", "false");
        parts.add("isPrivateFile", "true");
        parts.add("responseFields", "isPrivateFile");

        try {
            JsonNode response = client.post()
                    .uri(properties.uploadUrl())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(JsonNode.class);
            String fileId = response == null ? "" : response.path("fileId").asText();
            if (!fileId.matches("[A-Za-z0-9_-]{1,200}")
                    || !expectedPath.equals(response.path("filePath").asText())
                    || !response.path("isPrivateFile").asBoolean()) {
                if (fileId.matches("[A-Za-z0-9_-]{1,200}")) delete(new StoredDocument(fileId, expectedPath));
                log.warn("ImageKit returned incomplete document metadata for message {}", messageId);
                throw new GroupChatDocumentStorageException("Document upload could not be completed");
            }
            log.info("Uploaded group chat document for message {} in group {}", messageId, groupId);
            return new StoredDocument(fileId, expectedPath);
        } catch (RestClientResponseException exception) {
            String requestId = exception.getResponseHeaders() == null ? null
                    : exception.getResponseHeaders().getFirst("x-ik-requestId");
            log.warn("ImageKit upload returned HTTP {} for message {} (request ID {})",
                    exception.getStatusCode().value(), messageId, requestId);
            throw new GroupChatDocumentStorageException("Document storage is temporarily unavailable");
        } catch (RestClientException exception) {
            log.warn("ImageKit upload failed for message {}: {}", messageId,
                    exception.getClass().getSimpleName());
            throw new GroupChatDocumentStorageException("Document storage is temporarily unavailable");
        }
    }

    @Override
    public String signedUrl(String filePath, String fileName) {
        requireConfigured();
        if (!filePath.startsWith("/walkaround/group-chat-documents/")) {
            throw new GroupChatDocumentStorageException("Document path is invalid");
        }
        String expiry = String.valueOf(Instant.now().plus(documents.signedUrlTtl()).getEpochSecond());
        int extensionStart = fileName.lastIndexOf('.');
        String downloadName = extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
        String path = filePath.substring(1) + "?ik-attachment=true&ik-attachment-filename="
                + URLEncoder.encode(downloadName, StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(properties.privateKey().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            // ImageKit excludes attachment disposition parameters from signature verification.
            String signature = HexFormat.of().formatHex(mac.doFinal((filePath.substring(1) + expiry)
                    .getBytes(StandardCharsets.UTF_8)));
            return properties.urlEndpoint().replaceAll("/+$", "") + "/" + path
                    + "&ik-t=" + expiry + "&ik-s=" + signature;
        } catch (GeneralSecurityException exception) {
            throw new GroupChatDocumentStorageException("Document access is temporarily unavailable");
        }
    }

    @Override
    public void delete(StoredDocument document) {
        String fileId = document.fileId();
        if (properties.privateKey().isBlank()) return;
        if (!fileId.matches("[A-Za-z0-9_-]{1,200}")) return;
        try {
            client.delete()
                    .uri(properties.filesUrl() + "/" + fileId)
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Could not remove unused ImageKit file {}: {}", fileId,
                    exception.getClass().getSimpleName());
        }
    }

    private void requireConfigured() {
        if (properties.privateKey().isBlank() || properties.urlEndpoint().isBlank()) {
            throw new GroupChatDocumentStorageException("Document uploads are not configured");
        }
    }

    private String authorization() {
        String credentials = properties.privateKey() + ":";
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }
}
