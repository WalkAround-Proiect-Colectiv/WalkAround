package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.tripplan.model.GroupChatDocument;
import services.applicationapi.tripplan.model.GroupChatDocumentMetadata;

@Repository
public class JdbcGroupChatDocumentRepository implements GroupChatDocumentRepository {
    private final JdbcTemplate jdbc;
    public JdbcGroupChatDocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<GroupChatDocument> findByMessageId(UUID id) {
        return jdbc.query("SELECT * FROM trip_group_chat_documents WHERE message_id = ?", (rs, row) ->
            new GroupChatDocument(rs.getObject("message_id", UUID.class), rs.getString("file_id"),
                rs.getString("file_path"), rs.getString("content_sha256"),
                new GroupChatDocumentMetadata(rs.getString("file_name"), rs.getString("content_type"), rs.getLong("size_bytes"))), id)
            .stream().findFirst();
    }

    public void insert(GroupChatDocument document) {
        jdbc.update("""
            INSERT INTO trip_group_chat_documents
                (message_id, file_id, file_path, content_sha256, file_name, content_type, size_bytes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, document.messageId(), document.fileId(), document.filePath(), document.contentSha256(),
            document.metadata().fileName(), document.metadata().contentType(), document.metadata().sizeBytes());
    }
}
