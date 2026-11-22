package services.applicationapi.placesearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import services.applicationapi.configuration.MapPoiDatabase;
import services.applicationapi.placesearch.repository.NearbyPlaceRepository;

@Testcontainers(disabledWithoutDocker = true)
class NearbyPlaceRepositoryTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));

    private JdbcTemplate jdbc;
    private NearbyPlaceRepository repository;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS postgis; CREATE SCHEMA IF NOT EXISTS osm");
        jdbc.execute("DROP TABLE IF EXISTS osm.features");
        jdbc.execute("""
                CREATE TABLE osm.features (
                    osm_type text, osm_id bigint, kind text, class text, name text,
                    geom geometry(Point,3857))
                """);
        var database = mock(MapPoiDatabase.class);
        when(database.jdbcTemplate()).thenReturn(new NamedParameterJdbcTemplate(source));
        repository = new NearbyPlaceRepository(database);
    }

    @Test
    void filtersByActualMetersAtRomanianLatitudeAndSortsNearestFirst() {
        insert(1, "police", "Farther station", 46.77, 23.62);
        insert(2, "police", null, 46.77, 23.60);
        insert(3, "police", "Outside radius", 46.77, 23.64);
        insert(4, "cafe", "Not police", 46.77, 23.59);

        var stations = repository.findPolice(46.77, 23.59, 3000, 0, 51);

        assertThat(stations).extracting("id").containsExactly("n:2", "n:1");
        assertThat(stations.getFirst().name()).isEqualTo("Police station");
        assertThat(stations.getFirst().distanceMeters()).isBetween(750.0, 780.0);
        assertThat(stations.getLast().distanceMeters()).isBetween(2250.0, 2310.0);
        assertThat(stations.getFirst().latitude()).isCloseTo(46.77, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(repository.findPolice(46.77, 23.59, 3000, 1, 1)).containsExactly(stations.getLast());
        assertThat(repository.findPolice(46.77, 23.59, 100, 0, 51)).isEmpty();
    }

    private void insert(long id, String type, String name, double lat, double lon) {
        jdbc.update("""
                INSERT INTO osm.features VALUES ('n', ?, 'poi', ?, ?,
                    ST_Transform(ST_SetSRID(ST_MakePoint(?, ?),4326),3857))
                """, id, type, name, lon, lat);
    }
}
