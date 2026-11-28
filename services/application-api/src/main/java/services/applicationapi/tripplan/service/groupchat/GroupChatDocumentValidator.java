package services.applicationapi.tripplan.service.groupchat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.multipart.MultipartFile;
import services.applicationapi.tripplan.configurations.GroupChatDocumentProperties;
import services.applicationapi.tripplan.model.GroupChatDocumentMetadata;
import services.applicationapi.tripplan.service.exceptions.GroupChatInvalidMessageException;

@Component
@EnableConfigurationProperties(GroupChatDocumentProperties.class)
public class GroupChatDocumentValidator {
    private final GroupChatDocumentProperties properties;
    public GroupChatDocumentValidator(GroupChatDocumentProperties properties) { this.properties = properties; }

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public ValidatedDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Choose a nonempty PDF, TXT, DOCX, or CSV document");
        if (file.getSize() > properties.maxBytes()) throw invalid("The document exceeds the upload size limit");
        String name = file.getOriginalFilename();
        if (name == null) throw invalid("A document filename is required");
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isBlank() || name.length() > 200 || name.codePoints().anyMatch(c ->
                Character.isISOControl(c) || Character.getType(c) == Character.FORMAT)) {
            throw invalid("Use a filename of up to 200 characters without control characters");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!extension.equals("pdf") && !extension.equals("txt") && !extension.equals("docx") && !extension.equals("csv")) {
            throw invalid("Only PDF, TXT, DOCX, and CSV documents are supported");
        }
        try (var stream = file.getInputStream()) {
            byte[] bytes = stream.readNBytes((int) properties.maxBytes() + 1);
            if (bytes.length == 0 || bytes.length > properties.maxBytes()) throw invalid("The document exceeds the upload size limit");
            switch (extension) {
                case "pdf" -> {
                    String header = new String(bytes, 0, Math.min(bytes.length, 8), StandardCharsets.US_ASCII);
                    String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(bytes.length, 1024), StandardCharsets.US_ASCII);
                    if (!header.matches("%PDF-[12]\\.[0-9]") || !tail.contains("%%EOF")) throw invalid("The selected file is not a PDF document");
                }
                case "docx" -> {
                    if (bytes.length < 4 || bytes[0] != 0x50 || bytes[1] != 0x4B || bytes[2] != 0x03 || bytes[3] != 0x04) {
                        throw invalid("The selected file is not a DOCX document");
                    }
                }
                default -> {
                    String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                    if (text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
                        throw invalid(extension.equals("csv") ? "CSV documents must contain UTF-8 text" : "TXT documents must contain UTF-8 text");
                    }
                }
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new ValidatedDocument(bytes, extension, hash, new GroupChatDocumentMetadata(name, contentType(extension), bytes.length));
        } catch (CharacterCodingException exception) {
            throw invalid(extension.equals("csv") ? "CSV documents must contain UTF-8 text" : "TXT documents must contain UTF-8 text");
        } catch (IOException exception) {
            throw invalid("The document could not be read");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> DOCX_CONTENT_TYPE;
            case "csv" -> "text/csv";
            default -> "text/plain";
        };
    }

    private GroupChatInvalidMessageException invalid(String message) { return new GroupChatInvalidMessageException(message); }
    public record ValidatedDocument(byte[] bytes, String extension, String sha256, GroupChatDocumentMetadata metadata) {}
}
