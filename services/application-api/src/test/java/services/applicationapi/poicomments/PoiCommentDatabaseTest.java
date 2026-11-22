package services.applicationapi.poicomments;

import static org.assertj.core.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import services.applicationapi.poicomments.repository.PoiCommentRepository;
import services.applicationapi.poicomments.service.PoiCommentService;
import services.applicationapi.recommendation.repository.PostgisPoiRepositoryImpl;

@Testcontainers(disabledWithoutDocker = true)
class PoiCommentDatabaseTest {
        @Container static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse(
            System.getProperty("poi.comments.test-image", "postgres:18")).asCompatibleSubstituteFor("postgres"));
    private static JdbcTemplate jdbc;
    private static TransactionTemplate transaction;
    private static PoiCommentService service;
    private static PostgisPoiRepositoryImpl pois;
    private final UUID author = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @BeforeAll
    static void database() {
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE users(id uuid PRIMARY KEY, username varchar(50))");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V19__create_poi_comments.sql")).execute(dataSource);
        jdbc.execute("CREATE SCHEMA places; CREATE SCHEMA osm");
        jdbc.execute("CREATE TABLE places.published_pois(id uuid PRIMARY KEY)");
        jdbc.execute("CREATE TABLE places.published_osm_references(poi_id uuid NOT NULL, value text NOT NULL)");
        jdbc.execute("CREATE TABLE osm.features(osm_type text, osm_id bigint, kind text, minzoom integer)");
        var named = new NamedParameterJdbcTemplate(dataSource);
        pois = new PostgisPoiRepositoryImpl(named);
        service = new PoiCommentService(new PoiCommentRepository(named, jdbc), pois, "");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @BeforeEach
    void fixtures() {
        jdbc.execute("TRUNCATE users, poi_comment_subjects, places.published_pois, places.published_osm_references, osm.features CASCADE");
        jdbc.update("INSERT INTO users(id, username) VALUES (?, 'Walker'), (?, 'Other walker')", author, stranger);
        jdbc.execute("INSERT INTO osm.features VALUES ('N', 123, 'poi', 15), ('W', 123, 'poi', 15), ('N', 99, 'road', 4)");
    }

    @Test
    void migrationPersistsCommentsAndProtectsOwnership() {
        var created = inTransaction(() -> service.create("osm:n/123", author, "  A useful tip  "));
        var page = inTransaction(() -> service.list("osm:n/123", null));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().body()).isEqualTo("A useful tip");
        assertThat(page.items().getFirst().authorName()).isEqualTo("Walker");
        assertThatThrownBy(() -> inTransaction(() -> service.edit(created.id(), stranger, "Changed")))
                .isInstanceOf(ResponseStatusException.class);
        inTransaction(() -> service.edit(created.id(), author, "Updated"));
        assertThat(inTransaction(() -> service.list("osm:n/123", null)).items().getFirst().body()).isEqualTo("Updated");
        inTransaction(() -> { service.delete(created.id(), author); return null; });
        assertThat(inTransaction(() -> service.list("osm:n/123", null)).items()).isEmpty();
    }

    @Test
    void canonicalEnrichmentPreservesAndCombinesExistingOsmDiscussions() {
        inTransaction(() -> service.create("osm:n/123", author, "Node comment"));
        inTransaction(() -> service.create("osm:w/123", stranger, "Way comment"));
        UUID canonical = UUID.randomUUID();
        jdbc.update("INSERT INTO places.published_pois VALUES (?)", canonical);
        jdbc.update("INSERT INTO places.published_osm_references VALUES (?, 'node/123'), (?, 'way/123')", canonical, canonical);
        assertThat(inTransaction(() -> service.list(canonical.toString(), null)).items())
                .extracting(PoiCommentService.Comment::body).containsExactlyInAnyOrder("Node comment", "Way comment");
        assertThat(inTransaction(() -> service.list("osm:n/123", null)).items()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poi_comment_subjects", Integer.class)).isEqualTo(1);
        assertThat(pois.findCommentReferences("osm:n/99")).isEmpty();
        assertThat(pois.findCommentReferences("osm:r/123")).isEmpty();
    }

    @Test
    void cursorPagesAreDisjointEvenWhenTimestampsMatch() {
        UUID subject = UUID.randomUUID();
        jdbc.update("INSERT INTO poi_comment_subjects VALUES (?, now())", subject);
        jdbc.update("INSERT INTO poi_comment_aliases VALUES ('osm:n/123', ?)", subject);
        for (int index = 0; index < 23; index++) {
            jdbc.update("INSERT INTO poi_comments VALUES (?, ?, ?, ?, '2026-01-01T10:00:00Z', '2026-01-01T10:00:00Z')",
                    UUID.randomUUID(), subject, author, "Comment " + index);
        }
        var first = inTransaction(() -> service.list("osm:n/123", null));
        var second = inTransaction(() -> service.list("osm:n/123", first.nextCursor()));
        assertThat(first.items()).hasSize(20);
        assertThat(second.items()).hasSize(3);
        assertThat(second.nextCursor()).isNull();
        var identities = new HashSet<UUID>();
        first.items().forEach(comment -> identities.add(comment.id()));
        second.items().forEach(comment -> assertThat(identities.add(comment.id())).isTrue());
    }

    @Test
    void writeLimitPersistsAcrossRequestsAndReportsAreDeduplicated() {
        var created = inTransaction(() -> service.create("osm:n/123", author, "Tip"));
        inTransaction(() -> { service.report(created.id(), stranger, "Spam"); return null; });
        inTransaction(() -> { service.report(created.id(), stranger, "Spam"); return null; });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poi_comment_reports", Integer.class)).isEqualTo(1);
        for (int index = 1; index < 10; index++) inTransaction(() -> service.create("osm:n/123", author, "Tip"));
        assertThatThrownBy(() -> inTransaction(() -> service.create("osm:n/123", author, "Too many")))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(429));
        jdbc.update("UPDATE poi_comment_rate_limits SET window_start = now() - interval '2 minutes' WHERE user_id = ?", author);
        inTransaction(() -> service.create("osm:n/123", author, "Allowed again"));
        inTransaction(() -> { service.delete(created.id(), author); return null; });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM poi_comment_reports", Integer.class)).isZero();
    }

    private <Result> Result inTransaction(Supplier<Result> action) {
        return transaction.execute(status -> action.get());
    }
}