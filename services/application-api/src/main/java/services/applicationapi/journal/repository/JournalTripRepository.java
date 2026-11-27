package services.applicationapi.journal.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import services.applicationapi.journal.dto.JournalTrip;

@Repository
public class JournalTripRepository {
    public record Stored(JournalTrip trip, String fingerprint) {}
    private final JdbcTemplate jdbc;

    public JournalTripRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean lockOwner(UUID owner) {
        return !jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE",
                (result, row) -> result.getObject(1, UUID.class), owner).isEmpty();
    }

    public Optional<Stored> find(UUID owner, UUID id) {
        return jdbc.query("SELECT * FROM journal_trips WHERE owner_id = ? AND id = ?",
                (result, row) -> new Stored(trip(result), result.getString("request_fingerprint")), owner, id).stream().findFirst();
    }

    public List<JournalTrip> list(UUID owner, int page) {
        return jdbc.query("SELECT * FROM journal_trips WHERE owner_id = ? ORDER BY created_at DESC, id DESC LIMIT 21 OFFSET ?",
                (result, row) -> trip(result), owner, page * 20);
    }

    public JournalTrip create(UUID owner, JournalTrip trip, String fingerprint) {
        jdbc.update("""
                INSERT INTO journal_trips(id, owner_id, title, description, destination_city, country_code,
                    country_name, start_date, end_date, source_plan_id, request_fingerprint)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, trip.id(), owner, trip.title(), trip.description(), trip.destinationCity(), trip.countryCode(),
                trip.countryName(), trip.startDate(), trip.endDate(), trip.sourcePlanId(), fingerprint);
        return find(owner, trip.id()).orElseThrow().trip();
    }

    public JournalTrip update(UUID owner, JournalTrip trip) {
        jdbc.update("""
                UPDATE journal_trips SET title = ?, description = ?, destination_city = ?, country_code = ?,
                    country_name = ?, start_date = ?, end_date = ?, updated_at = clock_timestamp()
                WHERE owner_id = ? AND id = ?
                """, trip.title(), trip.description(), trip.destinationCity(), trip.countryCode(), trip.countryName(),
                trip.startDate(), trip.endDate(), owner, trip.id());
        return find(owner, trip.id()).orElseThrow().trip();
    }

    public int delete(UUID owner, UUID id) {
        return jdbc.update("DELETE FROM journal_trips WHERE owner_id = ? AND id = ?", owner, id);
    }

    private JournalTrip trip(ResultSet result) throws SQLException {
        return new JournalTrip(result.getObject("id", UUID.class), result.getString("title"), result.getString("description"),
                result.getString("destination_city"), result.getString("country_code"), result.getString("country_name"),
                result.getObject("start_date", java.time.LocalDate.class), result.getObject("end_date", java.time.LocalDate.class),
                result.getObject("source_plan_id", UUID.class), result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());
    }
}