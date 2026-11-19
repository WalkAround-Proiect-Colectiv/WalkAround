package services.applicationapi.tripplan.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.tripplan.model.GroupChatImage;

@Repository
public class JdbcGroupChatImageRepository implements GroupChatImageRepository {

    private final JdbcTemplate jdbc;

    public JdbcGroupChatImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GroupChatImage> findByMessageId(UUID messageId) {
        return jdbc.query("""
                SELECT message_id, imagekit_file_id, imagekit_file_path, content_sha256, width, height
                FROM trip_group_chat_images WHERE message_id = ?
                """, (rs, row) -> new GroupChatImage(
                rs.getObject("message_id", UUID.class), rs.getString("imagekit_file_id"),
                rs.getString("imagekit_file_path"), rs.getString("content_sha256"),
                rs.getInt("width"), rs.getInt("height")), messageId).stream().findFirst();
    }

    @Override
    public void insert(GroupChatImage image) {
        jdbc.update("""
                INSERT INTO trip_group_chat_images
                    (message_id, imagekit_file_id, imagekit_file_path, content_sha256, width, height)
                VALUES (?, ?, ?, ?, ?, ?)
                """, image.messageId(), image.fileId(), image.filePath(),
                image.contentSha256(), image.width(), image.height());
    }
}
