package services.applicationapi.tripplan.service.groupchat;

import java.nio.charset.StandardCharsets;
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
import services.applicationapi.tripplan.service.exceptions.GroupChatImageStorageException;
import tools.jackson.databind.JsonNode;

@Component
public class ImageKitGroupChatImageStorage implements GroupChatImageStorage {

    private static final Logger log = LoggerFactory.getLogger(ImageKitGroupChatImageStorage.class);

    private final RestClient client;
    private final ImageKitProperties properties;

    public ImageKitGroupChatImageStorage(@Qualifier("imageKitRestClient") RestClient client,
                                        ImageKitProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public UploadedImage upload(UUID groupId, UUID messageId, byte[] jpeg) {
        requireConfigured();
        String folder = "/walkaround/group-chats/" + groupId;
        String fileName = messageId + "-" + UUID.randomUUID() + ".jpg";
        String expectedPath = folder + "/" + fileName;

        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.IMAGE_JPEG);
        ByteArrayResource file = new ByteArrayResource(jpeg) {
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
                    || !"image".equals(response.path("fileType").asText())) {
                if (fileId.matches("[A-Za-z0-9_-]{1,200}")) delete(fileId);
                log.warn("ImageKit returned incomplete image metadata for message {}", messageId);
                throw new GroupChatImageStorageException("Image upload could not be completed");
            }
            log.info("Uploaded group chat image for message {} in group {}", messageId, groupId);
            return new UploadedImage(fileId, expectedPath);
        } catch (RestClientResponseException exception) {
            String requestId = exception.getResponseHeaders() == null ? null
                    : exception.getResponseHeaders().getFirst("x-ik-requestId");
            log.warn("ImageKit upload returned HTTP {} for message {} (request ID {})",
                    exception.getStatusCode().value(), messageId, requestId);
            throw new GroupChatImageStorageException("Image service is temporarily unavailable", exception);
        } catch (RestClientException exception) {
            log.warn("ImageKit upload failed for message {}: {}", messageId,
                    exception.getClass().getSimpleName());
            throw new GroupChatImageStorageException("Image service is temporarily unavailable", exception);
        }
    }

    @Override
    public String signedUrl(String filePath) {
        requireConfigured();
        if (!filePath.startsWith("/walkaround/group-chats/")) {
            throw new GroupChatImageStorageException("Image path is invalid");
        }
        String expiry = String.valueOf(Instant.now().plus(properties.signedUrlTtl()).getEpochSecond());
        String path = filePath.substring(1);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(properties.privateKey().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String signature = HexFormat.of().formatHex(mac.doFinal((path + expiry)
                    .getBytes(StandardCharsets.UTF_8)));
            return properties.urlEndpoint().replaceAll("/+$", "") + filePath
                    + "?ik-t=" + expiry + "&ik-s=" + signature;
        } catch (GeneralSecurityException exception) {
            throw new GroupChatImageStorageException("Image access is temporarily unavailable", exception);
        }
    }

    @Override
    public void delete(String fileId) {
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
            throw new GroupChatImageStorageException("Image uploads are not configured");
        }
    }

    private String authorization() {
        String credentials = properties.privateKey() + ":";
        return "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
    }
}
